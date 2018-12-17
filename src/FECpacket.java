import java.util.Arrays;
import java.util.Vector;

public class FECpacket {

    static int HEADER_SIZE = 12;
    static int HEADER_SIZE_Payload = 12;
    static int FEC_TYPE = 127; //FEX Payload type


    int fecGroupSize;       // FEC-Gruppengröße
    private int FEC_Packet_Size;

    public int payload_size;


    public byte[] payload;

    Vector<byte[]> rtpStack; // Puffer für Medienpakete
    Vector<byte[]> fecStack;   // Puffer für FEC-Pakete

    int correctedCount = 0;

    // SENDER --------------------------------------
    public FECpacket() {
        payload = new byte[15000];
        FEC_Packet_Size = 0;
    }

    // RECEIVER ------------------------------------
    public FECpacket(int fecGroupSize) {
        this.fecGroupSize = fecGroupSize;
        rtpStack = new Vector<byte[]>();
        fecStack = new Vector<byte[]>();
    }

    // ----------------------------------------------
    // *** SENDER ***
    // ----------------------------------------------

    // speichert Nutzdaten zur FEC-Berechnung
    public void setdata(byte[] data, int data_length) {

        if (FEC_Packet_Size == 0) {
            FEC_Packet_Size = data_length;
            payload = Arrays.copyOf(data, FEC_Packet_Size);
        } else {
            FEC_Packet_Size = Math.max(FEC_Packet_Size, data_length);
            payload = Arrays.copyOf(payload, FEC_Packet_Size);
            for (int i = 0; i < data_length; i++) {
                payload[i] = (byte) (payload[i] ^ data[i]);
            }
        }
    }

    // holt fertiges FEC-Paket, Rückgabe: Paketlänge
    public int getdata(byte[] data) {
        int result = FEC_Packet_Size;
        for (int i = 0; i < FEC_Packet_Size; i++) {
            data[i] = payload[i];
        }

        FEC_Packet_Size = 0;

        return result;
    }


    // ------------------------------------------------
    // *** RECEIVER ***
    // ------------------------------------------------
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvdata(int nr, byte[] data) {
        while (rtpStack.size() < nr + 1) {
            rtpStack.add(new byte[1]);
        }
        rtpStack.set(nr, Arrays.copyOf(data, data.length));
    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe
    public void rcvfec(int nr, byte[] data) {
        while (fecStack.size() < nr + 1) {
            fecStack.add(new byte[1]);
        }
        fecStack.set(nr, Arrays.copyOf(data, data.length));
        while (rtpStack.size() < nr + 1) {
            rtpStack.add(new byte[1]);
        }
    }

    private byte[] getFecPackage(int nr) {
        byte[] result = fecStack.get(nr);
        return Arrays.copyOf(result, result.length);
    }

    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)
    public byte[] getjpeg(int nr) {
        byte[] result = rtpStack.get(nr);
        if (result.length == 1) {
            int fecNr = getFecNumber(nr);
            int existing = fecGroupSize;
            if (fecStack.size() >= fecNr) {
                byte[] fecData = getFecPackage(fecNr);
                for (int i = fecNr - fecGroupSize + 1; i <= fecNr; i++) {
                    byte[] frame = rtpStack.get(i);
                    if (frame.length > 1) {
                        for (int n = 0; n < frame.length; n++) {
                            fecData[n] = (byte) (fecData[n] ^ frame[n]);
                        }
                    } else {
                        existing--;
                    }
                }
                if (existing >= fecGroupSize - 1) {
                    rtpStack.set(nr, fecData);
                    result = Arrays.copyOf(fecData, fecData.length);
                    correctedCount++;
                }
            }
        }
        return result;
    }

    private int getFecNumber(int rtpNr) {
        while (rtpNr % fecGroupSize != 0) {
            rtpNr++;
        }
        return rtpNr;
    }

    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        return correctedCount;
    }
}
