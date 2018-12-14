import java.util.Arrays;
import java.util.Vector;

public class FECpacket {

    static int HEADER_SIZE = 12;
    static int HEADER_SIZE_Payload = 12;
    static int FEC_TYPE = 127; //FEX Payload type




    int FEC_group;       // FEC-Gruppengröße
    private int FEC_Packet_Size;

    public int payload_size;


    public byte[] payload;

    Vector<byte[]> mediastack; // Puffer für Medienpakete
    Vector<byte[]> fecstack;   // Puffer für FEC-Pakete

    // SENDER --------------------------------------
    public FECpacket() {
        payload = new byte[15000];
        FEC_Packet_Size=0;
    }

    // RECEIVER ------------------------------------
    public FECpacket(int FEC_group) {
        this.FEC_group = FEC_group;
        mediastack = new Vector<byte[]>();
        fecstack = new Vector<byte[]>();
    }

    // ----------------------------------------------
    // *** SENDER ***
    // ----------------------------------------------

    // speichert Nutzdaten zur FEC-Berechnung
    public void setdata(byte[] data, int data_length) {

        if(FEC_Packet_Size==0){
            for (int i = 0; i < data_length; i++) {
                payload[i] = data[i];
            }
        }
        else {
            for (int i = 0; i < data_length; i++) {
                payload[i] = (byte) (payload[i] ^ data[i]);
            }
        }
        if(FEC_Packet_Size<data_length) {
            FEC_Packet_Size = data_length;
        }
    }

    // holt fertiges FEC-Paket, Rückgabe: Paketlänge
    public int getdata(byte[] data) {


        for (int i = 0; i < FEC_Packet_Size; i++) {
            data[i] = payload[i];
            payload[i]=0;
        }

        FEC_Packet_Size=0;

        return (payload_size);
    }


    // ------------------------------------------------
    // *** RECEIVER ***
    // ------------------------------------------------
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvdata(int nr, byte[] data) {
        byte[] temp = new byte[15000];
        for (int i = 0; i < data.length; i++) {
            temp[i] = data[i];
        }
        while(mediastack.size()<nr){
            byte[] empty = new byte[5];
            mediastack.add(empty);
        }
        mediastack.add(temp);
    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe
    public void rcvfec(int nr, byte[] data) {
        byte[] temp = new byte[15000];
        for (int i = 0; i < data.length; i++) {
            temp[i] = data[i];
        }

        while((mediastack.size()-4)<(fecstack.size()*FEC_group)){
            byte[] empty = new byte[5];
            fecstack.add(empty);
        }

        fecstack.add(temp);

        getjpeg();

    }

    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)
    public void getjpeg() {
        byte[] result = new byte[15000];
        for (int j = 0; j < result.length; j++) {
            fecstack.lastElement()[j] = result[j];
        }


        int lost=0, packagedLost=0;
        for(int i = (fecstack.size()-1)*FEC_group; i<=fecstack.size()*FEC_group-1;i++){
            if(mediastack.elementAt(i).length<100){
                lost = i;
                packagedLost++;
            }else{
                mediastack.set(lost,result);
                /*for (int j = 0; j < mediastack.elementAt(j).length; j++) {
                    result[j] = (byte) (result[j] ^ mediastack.elementAt(i)[j]);
                }*/

            }
        }
        /*if(packagedLost==1){
            for (int j = 0; j < result.length; j++) {

                mediastack.elementAt(lost)[j] = result[j];
            }

        }*/
    }

    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        return 0;
    }
}
