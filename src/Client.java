/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
---------------------- */

import com.sun.xml.internal.ws.util.StringUtils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Client {

    // GUI
    // ----
    private JFrame f = new JFrame("Client");
    private JButton setupButton = new JButton("Setup");
    private JButton playButton = new JButton("Play");
    private JButton pauseButton = new JButton("Pause");
    private JButton tearButton = new JButton("Teardown");
    private JButton optionsButton = new JButton("Options");
    private JButton describeButtons = new JButton("Describe");
    private JPanel mainPanel = new JPanel();
    private JPanel buttonPanel = new JPanel();
    private JLabel iconLabel = new JLabel();
    private JLabel statsLabel = new JLabel();
    private ImageIcon icon;
    private FECpacket cue = null;

    // Statistical variables
    private int droppedPackages = 0;
    private int receivedPackages = 0;
    private int uncorrectablePackages = 0; // Packages we couldn't correct

    // RTP variables:
    // ----------------
    private DatagramPacket rcvdp; // UDP packet received from the server
    private DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
    private static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

    private Timer timer; // timer used to receive data from the UDP socket
    private byte[] buf; // buffer used to store data received from the server

    // RTSP variables
    // ----------------
    // rtsp states
    private static final int INIT = 0;
    private static final int READY = 1;
    private static final int PLAYING = 2;
    private static final int TEARDOWN = 3;
    private static int state; // RTSP state == INIT or READY or PLAYING
    private Socket RTSPsocket; // socket used to send/receive RTSP messages
    // input and output stream filters
    private static BufferedReader RTSPBufferedReader;
    private static BufferedWriter RTSPBufferedWriter;
    private static String VideoFileName; // video file to request to the server
    private int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
    private int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

    private static final String CRLF = "\r\n";

    private static String rtspBody;

    // Video constants:
    // ------------------
    private static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
    private static int FEC_TYPE = 127;

    // --------------------------
    // Constructor
    // --------------------------
    public Client() {

        // build GUI
        // --------------------------

        // Frame
        f.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        System.exit(0);
                    }
                });

        // Buttons
        buttonPanel.setLayout(new GridLayout(1, 0));
        buttonPanel.add(setupButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(tearButton);
        buttonPanel.add(optionsButton);
        buttonPanel.add(describeButtons);
        setupButton.addActionListener(new setupButtonListener());
        playButton.addActionListener(new playButtonListener());
        pauseButton.addActionListener(new pauseButtonListener());
        tearButton.addActionListener(new tearButtonListener());
        optionsButton.addActionListener(new optionsButtonListener());
        describeButtons.addActionListener(new describeButtonListener());

        //fec handler
        //cue = new FECpacket(4);

        // Image display label
        iconLabel.setIcon(null);

        // Restrict width of stats label
        statsLabel.setPreferredSize(new Dimension(150, 200));

        // frame layout
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(iconLabel, BorderLayout.CENTER);
        mainPanel.add(statsLabel, BorderLayout.LINE_END);
        mainPanel.add(buttonPanel, BorderLayout.PAGE_END);

        f.getContentPane().add(mainPanel, BorderLayout.CENTER);
        f.setSize(new Dimension(540, 370));
        f.setVisible(true);

        // init timer
        // --------------------------
        timer = new Timer(20, new timerListener());
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        // allocate enough memory for the buffer used to receive data from the server
        buf = new byte[15000];
    }

    // ------------------------------------
    // main
    // ------------------------------------
    public static void main(String argv[]) throws Exception {
        // Create a Client object
        Client theClient = new Client();

        // get server RTSP port and IP address from the command line
        // ------------------
        int RTSP_server_port = Integer.parseInt(argv[1]);
        String ServerHost = argv[0];
        InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

        // get video filename to request:
        VideoFileName = argv[2];

        // Establish a TCP connection with the server to exchange RTSP messages
        // ------------------
        theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

        // Set input and output stream filters:
        RTSPBufferedReader =
                new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
        RTSPBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

        // init RTSP state:
        state = INIT;
    }

    // ------------------------------------
    // Handler for buttons
    // ------------------------------------

    // TODO TO COMPLETE

    // Handler for Setup button
    // -----------------------
    private class setupButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            // System.out.println("Setup Button pressed !");

            if (state == INIT) {
                // Init non-blocking RTPsocket that will be used to receive data
                try {
                    RTPsocket = new DatagramSocket(new InetSocketAddress(RTP_RCV_PORT));
                    // Set timeout to 5ms
                    RTPsocket.setSoTimeout(5);
                } catch (SocketException se) {
                    System.out.println("Socket exception: " + se);
                    System.exit(0);
                }

                // init RTSP sequence number
                RTSPSeqNb = 1;

                // Request a description (used for FEC Size)
                send_RTSP_request("DESCRIBE");
                if (parse_server_response() != 200) {
                    System.out.println("Invalid Server Response");
                    System.exit(0);
                } else {
                    int FECGroupSize = 2;
                    StringTokenizer tokenizer = new StringTokenizer(rtspBody, "=" + CRLF);
                    while (tokenizer.hasMoreTokens()) {
                        String token = tokenizer.nextToken();
                        if (token.equals("a")) { // Search for a general sdp attribute
                            token = tokenizer.nextToken();
                            if (token.startsWith("FEC:")) { // Check if it is our FEC attribute
                                FECGroupSize = new Integer(token.substring(4)); // Convert everything after the : into an int
                                break;
                            }
                        }
                    }
                    cue = new FECpacket(FECGroupSize);
                }


                // Send SETUP message to the server
                send_RTSP_request("SETUP");

                // Wait for the response
                if (parse_server_response() != 200) System.out.println("Invalid Server Response");
                else {
                    // Change client state
                    state = READY;
                    System.out.println("New RTSP state: READY");
                }
            } // else if state != INIT then do nothing
        }
    }

    // Handler for Play button
    // -----------------------
    class playButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            System.out.println("Play Button pressed!");

            if (state == READY) {
                // increase RTSP sequence number
                RTSPSeqNb++;

                // Send PLAY message to the server
                send_RTSP_request("PLAY");

                // Wait for the response
                if (parse_server_response() != 200) System.out.println("Invalid Server Response");
                else {
                    // change RTSP state and print out new state
                    state = PLAYING;
                    System.out.println("New RTSP state: PLAYING");

                    // start the timer
                    timer.start();
                }
            } // else if state != READY then do nothing
        }
    }

    // Handler for Pause button
    // -----------------------
    private class pauseButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            System.out.println("Pause Button pressed!");

            if (state == PLAYING) {
                // increase RTSP sequence number
                RTSPSeqNb++;

                // Send PAUSE message to the server
                send_RTSP_request("PAUSE");

                // Wait for the response
                if (parse_server_response() != 200) System.out.println("Invalid Server Response");
                else {
                    // change RTSP state and print out new state
                    state = READY;
                    System.out.println("New RTSP state: READY");

                    // stop the timer
                    timer.stop();
                }
            }
            // else if state != PLAYING then do nothing
        }
    }

    class optionsButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            System.out.println("Options Button pressed!");
            send_RTSP_request("OPTIONS");
            if (parse_server_response() != 200) System.out.println("Invalid Server Response");
            else {
                iconLabel.setText("<html>" + rtspBody.replace(CRLF, "<br>"));
            }
        }
    }

    class describeButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            System.out.println("Describe Button pressed!");
            send_RTSP_request("DESCRIBE");
            if (parse_server_response() != 200) System.out.println("Invalid Server Response");
            else {
                iconLabel.setText("<html>" + rtspBody.replace(CRLF, "<br>"));
            }
        }
    }

    // Handler for Teardown button
    // -----------------------
    class tearButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            System.out.println("Teardown Button pressed!");

            // increase RTSP sequence number
            RTSPSeqNb++;

            // Send TEARDOWN message to the server
            send_RTSP_request("TEARDOWN");

            // Wait for the response
            if (parse_server_response() != 200) System.out.println("Invalid Server Response");
            else {
                // change RTSP state and print out new state
                state = TEARDOWN;
                System.out.println("New RTSP state: TEARDOWN");

                // stop the timer
                timer.stop();

                // exit
                System.exit(0);
            }
        }
    }

    // ------------------------------------
    // Handler for timer
    // ------------------------------------

    class timerListener implements ActionListener {

        private int lastReceivedPackage = 0;

        private int currentFrame = 1;

        private int retries = 0;

        public void actionPerformed(ActionEvent e) {

            // Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(buf, buf.length);

            try {
                // receive the DP from the socket:
                RTPsocket.receive(rcvdp);

                // create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
                receivedPackages++;

                // print important header fields of the RTP packet received:
                System.out.println(
                        "Got RTP packet with SeqNum # "
                                + rtp_packet.getsequencenumber()
                                + " TimeStamp "
                                + rtp_packet.gettimestamp()
                                + " ms, of type "
                                + rtp_packet.getpayloadtype());

                // print header bitstream:
                rtp_packet.printheader();

                if (rtp_packet.getsequencenumber() - lastReceivedPackage > 1) {
                    droppedPackages += rtp_packet.getsequencenumber() - lastReceivedPackage - 1;
                }
                lastReceivedPackage = rtp_packet.getsequencenumber();

                // Putting all of this in one format string makes it utterly unreadable
                String stats =
                        String.format("Received: %d", receivedPackages) + "<br>" +
                                String.format("Dropped: %d (%.1f%%)", droppedPackages, droppedPackages / (double) (receivedPackages + droppedPackages) * 100) + "<br>" +
                                String.format("Corrected: %d", cue.getNrCorrected()) + "<br>" +
                                String.format("Uncorrectable: %d", uncorrectablePackages);
                statsLabel.setText("<html>" + stats);

                if (rtp_packet.PayloadType == MJPEG_TYPE) {
                    // get the payload bitstream from the RTPpacket object
                    int payload_length = rtp_packet.getpayload_length();
                    byte[] payload = new byte[payload_length];

                    rtp_packet.getpayload(payload);
                    cue.rcvdata(rtp_packet.getsequencenumber(), payload);
                    payload = cue.getjpeg(currentFrame);

                    if (payload.length > 1) {
                        // get an Image object from the payload bitstream
                        Toolkit toolkit = Toolkit.getDefaultToolkit();
                        Image image = toolkit.createImage(payload, 0, payload_length);

                        // display the image as an ImageIcon object
                        icon = new ImageIcon(image);
                        icon.paintIcon(iconLabel, iconLabel.getGraphics(), 0, 0);
                        currentFrame++;
                    } else {
                        retries++;
                        uncorrectablePackages++;
                        if (retries > cue.fecGroupSize) {
                            currentFrame = rtp_packet.getsequencenumber();
                        }
                    }
                } else if (rtp_packet.PayloadType == FEC_TYPE) {
                    // get the payload bitstream from the RTPpacket object
                    int payload_length = rtp_packet.getpayload_length();
                    byte[] payload = new byte[payload_length];
                    rtp_packet.getpayload(payload);
                    cue.rcvfec(rtp_packet.getsequencenumber(), payload);
                }
            } catch (InterruptedIOException iioe) {
                // System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: " + ioe);
                ioe.printStackTrace();
            }
        }
    }

    // ------------------------------------
    // Parse Server Response
    // ------------------------------------
    private int parse_server_response() {
        int reply_code = 0;

        try {
            String line = "";
            // parse status line and extract the reply_code:
            line = RTSPBufferedReader.readLine();
            // System.out.println("RTSP Client - Received from Server:");
            System.out.println(line);

            StringTokenizer tokens = new StringTokenizer(line);
            tokens.nextToken(); // skip over the RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());

            // if reply code is OK get and print the 2 other lines
            if (reply_code == 200) {
                line = RTSPBufferedReader.readLine();
                System.out.println(line);

                line = RTSPBufferedReader.readLine();
                System.out.println(line);

                // if state == INIT gets the Session Id from the SessionLine
                tokens = new StringTokenizer(line);
                tokens.nextToken(); // skip over the Session:
                RTSPid = Integer.parseInt(tokens.nextToken());
            }

            rtspBody = "";
            do {
                line = RTSPBufferedReader.readLine();
                rtspBody += line + CRLF;
                System.out.println(line);
            } while (!line.isEmpty());
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        }

        return (reply_code);
    }

    // ------------------------------------
    // Send RTSP Request
    // ------------------------------------

    // TODO TO COMPLETE

    private void send_RTSP_request(String request_type) {
        try {
            // Use the RTSPBufferedWriter to write to the RTSP socket

            // write the request line:
            RTSPBufferedWriter.write(String.format("%s %s RTSP/1.0" + CRLF, request_type, VideoFileName));

            // write the CSeq line:
            RTSPBufferedWriter.write(String.format("CSeq %d" + CRLF, RTSPSeqNb));

            // check if request_type is equal to "SETUP" and in this case write the Transport: line
            // advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
            String nextLine = "";
            if (request_type.equals("SETUP")) {
                nextLine = String.format("Transport: RTP/UDP; client_port=%d", RTP_RCV_PORT);
            } else { // otherwise, write the Session line from the RTSPid field
                nextLine = String.format("Session: %d", RTSPid);
            }
            RTSPBufferedWriter.write(nextLine + CRLF);
            RTSPBufferedWriter.write(CRLF);

            // send end of request corresponding to servers behavior
            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        }
    }
} // end of Class Client
