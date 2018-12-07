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
        byte[] temp = new byte[0];
        for (int i = 0; i < data.length; i++) {
            temp[i] = data[i];
        }
        mediastack.add(temp);

    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe
    public void rcvfec(int nr, byte[] data) {
        byte[] temp = new byte[0];
        for (int i = 0; i < data.length; i++) {
            temp[i] = data[i];
        }
        fecstack.add(temp);
    }

    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)
    public byte[] getjpeg(int nr) {
        byte[] result = new byte[0];
        return result;
    }

    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        return 0;
    }
}
