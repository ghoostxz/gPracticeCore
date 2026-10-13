package br.gcelestial.practicecore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Party {
    private UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private boolean open;
    /** Convites pendentes: jogador -> timestamp de expiracao. */
    private final Map<UUID, Long> invites = new HashMap<>();

    public Party(UUID leader, boolean open) {
        this.leader = leader;
        this.open = open;
        this.members.add(leader);
    }

    public UUID leader() { return leader; }
    public void setLeader(UUID id) { this.leader = id; }
    public boolean isLeader(UUID id) { return leader.equals(id); }
    public List<UUID> members() { return members; }
    public int size() { return members.size(); }
    public boolean contains(UUID id) { return members.contains(id); }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Map<UUID, Long> invites() { return invites; }

    public boolean hasValidInvite(UUID id) {
        Long exp = invites.get(id);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            invites.remove(id);
            return false;
        }
        return true;
    }
}
