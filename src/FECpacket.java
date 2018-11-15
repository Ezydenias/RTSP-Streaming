import java.util.Arrays;

public class FECpacket {

    int FEC_group;       // FEC-Gruppengröße

    byte[][] mediastack; // Puffer für Medienpakete
    byte[][] fecstack;   // Puffer für FEC-Pakete

    // SENDER --------------------------------------
    public FECpacket() {
        this(0);
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
    }

    // holt fertiges FEC-Paket, Rückgabe: Paketlänge
    public int getdata(byte[] data) {
        return 0;
    }


    // ------------------------------------------------
    // *** RECEIVER ***
    // ------------------------------------------------
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvdata(int nr, byte[] data) {
    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe
    public void rcvfec(int nr, byte[] data) {
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
