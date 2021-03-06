/* ------------------
Server
usage: java Server [RTSP listening port]
---------------------- */

import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Server extends JFrame implements ActionListener {

    // RTP variables:
    // ----------------
    DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
    DatagramPacket senddp; // UDP packet containing the video frames

    InetAddress ClientIPAddr; // Client IP address
    int RTP_dest_port = 0; // destination port for RTP packets  (given by the RTSP Client)

    // GUI:
    // ----------------
    JLabel label;

    // Video variables:
    // ----------------
    int imagenb = 0; // image nb of the image currently transmitted
    VideoStream video; // VideoStream object used to access video frames
    static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
    static int FRAME_PERIOD = 40; // Frame period of the video to stream, in ms
    static int VIDEO_LENGTH = 500; // length of the video in frames
    static int FEC_TYPE = 127;
    static int FECSize = 4;

    private Timer timer; // timer used to send the images at the video frame rate
    private JSpinner frameDropSpinner;
    private JSpinner fecSizeSpinner;
    private FECpacket FEC;
    byte[] buf; // buffer used to store the images to send to the client

    // RTSP variables
    // ----------------
    // rtsp states
    static final int INIT = 0;
    static final int READY = 1;
    static final int PLAYING = 2;
    // rtsp message types
    static final int SETUP = 3;
    static final int PLAY = 4;
    static final int PAUSE = 5;
    static final int TEARDOWN = 6;
    static final int OPTIONS = 7;
    static final int DESCRIBE = 8;

    static int state; // RTSP Server state == INIT or READY or PLAY
    Socket RTSPsocket; // socket used to send/receive RTSP messages
    // input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; // video file requested from the client
    static int RTSP_ID = 123456; // ID of the RTSP session
    int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

    static Random rand;

    static final String CRLF = "\r\n";

    class fecSizeChangeListener implements ChangeListener {

      @Override
      public void stateChanged(ChangeEvent e) {
        FECSize = (int) fecSizeSpinner.getModel().getValue();
        System.out.println("FEC group size is now " + FECSize);
      }
    }

    // --------------------------------
    // Constructor
    // --------------------------------
    public Server() {

        // init Frame
        super("Server");

        // init Timer
        timer = new Timer(FRAME_PERIOD, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        // allocate memory for the sending buffer
        buf = new byte[15000];

        FEC = new FECpacket(FECSize);
        // Handler to close the main window
        addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        // stop the timer and exit
                        timer.stop();
                        System.exit(0);
                    }
                });

        // GUI:
        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));

        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label);

        SpinnerModel frameDropModel = new SpinnerNumberModel(0.1, 0.00, 1.00, 0.05);
        frameDropSpinner = new JSpinner(frameDropModel);
        getContentPane().add(frameDropSpinner);

        SpinnerModel fecSizeModel = new SpinnerNumberModel(4, 2, 32, 1);
        fecSizeSpinner = new JSpinner(fecSizeModel);
        fecSizeSpinner.addChangeListener(new fecSizeChangeListener());
        getContentPane().add(fecSizeSpinner);
    }

    // ------------------------------------
    // main
    // ------------------------------------
    public static void main(String argv[]) throws Exception {
        // create a Server object
        Server theServer = new Server();

        // show GUI:
        theServer.pack();
        theServer.setVisible(true);

        // get RTSP socket port from the command line
        int RTSPport = Integer.parseInt(argv[0]);

        // Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(RTSPport);
        theServer.RTSPsocket = listenSocket.accept();
        listenSocket.close();

        // Get Client IP address
        theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

        // Initiate RTSPstate
        state = INIT;

        // Set input and output stream filters:
        RTSPBufferedReader =
                new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()));
        RTSPBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()));

        rand = new Random();

        // Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        while (!done) {
            request_type = theServer.parse_RTSP_request(); // blocking

            if (request_type == SETUP) {
                done = true;

                // update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");

                // Send response
                theServer.send_RTSP_response();

                // init the VideoStream object:
                theServer.video = new VideoStream(VideoFileName);

                // init RTP socket
                theServer.RTPsocket = new DatagramSocket();
            } else if (request_type == OPTIONS) {
                theServer.handleOptions();
            } else if (request_type == DESCRIBE) {
                theServer.handleDescribe();
            }
        }

        // loop to handle RTSP requests
        while (true) {
            // parse the request
            request_type = theServer.parse_RTSP_request(); // blocking

            if ((request_type == PLAY) && (state == READY)) {
                // send back response
                theServer.send_RTSP_response();
                // start timer
                theServer.timer.start();
                // update state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");
            } else if ((request_type == PAUSE) && (state == PLAYING)) {
                // send back response
                theServer.send_RTSP_response();
                // stop timer
                theServer.timer.stop();
                // update state
                state = READY;
                System.out.println("New RTSP state: READY");
            } else if (request_type == TEARDOWN) {
                // send back response
                theServer.send_RTSP_response();
                // stop timer
                theServer.timer.stop();
                // close sockets
                theServer.RTSPsocket.close();
                theServer.RTPsocket.close();

                System.exit(0);
            } else if (request_type == OPTIONS) {
                theServer.handleOptions();
            } else if (request_type == DESCRIBE) {
                theServer.handleDescribe();
            }
        }
    }

    private void handleOptions() {
        String[] body = {
                "Public: DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN"
        };
        send_RTSP_response(body);
    }

    private void handleDescribe() {
        String[] body = {
                "Content-Type: application/sdp",
                "v=0",
                "o=- " + RTSP_ID + " 1 IN IP4 127.0.0.1",
                "s=Demo Movie",
                "i=HTW Dresden RTSP demo movie",
                "m=video 25000 udp " + MJPEG_TYPE,
                "a=FEC:" + FECSize
        };
        send_RTSP_response(body);
    }

    // ------------------------
    // Handler for timer
    // ------------------------
    public void actionPerformed(ActionEvent e) {

        // if the current image nb is less than the length of the video
        if (imagenb < VIDEO_LENGTH) {
            // update current imagenb
            imagenb++;

            try {
                // get next frame to send from the video, as well as its size
                int image_length = video.getnextframe(buf);

                // Builds an RTPpacket object containing the frame
                RTPpacket rtp_packet =
                        new RTPpacket(MJPEG_TYPE, imagenb, imagenb * FRAME_PERIOD, buf, image_length);

                // get to total length of the full rtp packet to send
                int packet_length = rtp_packet.getlength();

                // retrieve the packet bitstream and store it in an array of bytes
                byte[] packet_bits = new byte[packet_length];
                rtp_packet.getpacket(packet_bits);


                // send the packet as a DatagramPacket over the UDP socket
                senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);

                float v = rand.nextFloat();
                if (v > (Double) frameDropSpinner.getModel().getValue()) {
                    RTPsocket.send(senddp);
                    // System.out.println("Send frame #"+imagenb);
                    // print the header bitstream
                    rtp_packet.printheader();
                } else {
                    System.out.println("Dropped frame " + imagenb);
                }


                // update GUI
                label.setText("Send frame #" + imagenb);


                if (imagenb % FECSize == 0) {
                    FEC.setdata(packet_bits, packet_length);
                    image_length = FEC.getdata(buf);
                    RTPpacket fec_packet =
                            new RTPpacket(FEC_TYPE, imagenb, imagenb * FRAME_PERIOD, buf, image_length);
                    packet_length = fec_packet.getlength();
                    packet_bits = new byte[packet_length];
                    fec_packet.getpacket(packet_bits);
                    senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
                    RTPsocket.send(senddp);
                    // System.out.println("Send frame #"+imagenb);
                    // print the header bitstream
                    fec_packet.printheader();
                    label.setText("Send FEC #" + imagenb);
                } else {
                    FEC.setdata(packet_bits, packet_length);
                }

            } catch (Exception ex) {
                System.out.println("Exception caught: " + ex);
                ex.printStackTrace();
                System.exit(0);
            }
        } else {
            // if we have reached the end of the video file, stop the timer
            timer.stop();
        }
    }

    // ------------------------------------
    // Parse RTSP Request
    // ------------------------------------
    private int parse_RTSP_request() {
        int request_type = -1;
        try {
            // parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            // System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            // convert to request_type structure:
            if (request_type_string.equals("SETUP")) request_type = SETUP;
            else if (request_type_string.equals("PLAY")) request_type = PLAY;
            else if (request_type_string.equals("PAUSE")) request_type = PAUSE;
            else if (request_type_string.equals("TEARDOWN")) request_type = TEARDOWN;
            else if (request_type_string.equals("OPTIONS")) request_type = OPTIONS;
            else if (request_type_string.equals("DESCRIBE")) request_type = DESCRIBE;

            if (request_type == SETUP) {
                // extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }

            // parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());

            // get LastLine
            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);

            if (request_type == SETUP) {
                // extract RTP_dest_port from LastLine
                tokens = new StringTokenizer(LastLine);
                tokens.nextToken("=");
                RTP_dest_port = Integer.parseInt(tokens.nextToken());
            }
            // else LastLine will be the SessionId line ... do not check for now.
            while (!LastLine.isEmpty()) {
                LastLine = RTSPBufferedReader.readLine();
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        }
        return (request_type);
    }

    private void send_RTSP_response() {
        String[] b = {};
        send_RTSP_response(b);
    }

    // ------------------------------------
    // Send RTSP Response
    // ------------------------------------
    private void send_RTSP_response(String[] body) {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
            for (int i = 0; i < body.length; i++) {
                RTSPBufferedWriter.write(body[i] + CRLF);
            }
            RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.flush();
            // System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        }
    }
}
