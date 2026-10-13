package br.gcelestial.practicecore;

import java.io.File;

public record Arena(String modeId, String name, File schematic, Spawn p1, Spawn p2) {
    /** Posicao RELATIVA a origem da schematic (ponto onde voce deu //copy). */
    public record Spawn(double x, double y, double z, float yaw, float pitch) {}
}
