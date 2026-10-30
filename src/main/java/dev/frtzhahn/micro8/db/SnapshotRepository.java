package dev.frtzhahn.micro8.db;

import dev.frtzhahn.micro8.cpu.CpuCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite persistence repository for Workspace Snapshots and onboarding app settings.
 */
public final class SnapshotRepository {

    public static final String KEY_HIDE_ONBOARDING = "hide_onboarding";

    private final DatabaseManager dbManager;

    public SnapshotRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    public boolean existsByName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM workspace_snapshots WHERE name = ? LIMIT 1;";
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void saveSnapshot(WorkspaceSnapshot snapshot, boolean allowOverwrite) throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String cleanName = snapshot.name().trim();
        String timestamp = snapshot.createdAt().isBlank() ? Instant.now().toString() : snapshot.createdAt();

        try (Connection conn = dbManager.openConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                boolean exists = false;
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT 1 FROM workspace_snapshots WHERE name = ? LIMIT 1;"
                )) {
                    checkPs.setString(1, cleanName);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists && !allowOverwrite) {
                    throw new IllegalStateException("Snapshot '" + cleanName + "' already exists");
                }

                String upsertSql = """
                        INSERT INTO workspace_snapshots (
                            name, created_at, assembly_source, ram_blob,
                            reg_a, reg_b, pc, mar, ir, out_reg,
                            zero_flag, carry_flag, t_state, cycle_count
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(name) DO UPDATE SET
                            created_at = excluded.created_at,
                            assembly_source = excluded.assembly_source,
                            ram_blob = excluded.ram_blob,
                            reg_a = excluded.reg_a,
                            reg_b = excluded.reg_b,
                            pc = excluded.pc,
                            mar = excluded.mar,
                            ir = excluded.ir,
                            out_reg = excluded.out_reg,
                            zero_flag = excluded.zero_flag,
                            carry_flag = excluded.carry_flag,
                            t_state = excluded.t_state,
                            cycle_count = excluded.cycle_count
                        """;
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    ps.setString(1, cleanName);
                    ps.setString(2, timestamp);
                    ps.setString(3, snapshot.assemblySource());
                    ps.setBytes(4, snapshot.ramBlob());
                    ps.setInt(5, snapshot.regA() & 0xFF);
                    ps.setInt(6, snapshot.regB() & 0xFF);
                    ps.setInt(7, snapshot.pc() & 0xFF);
                    ps.setInt(8, snapshot.mar() & 0xFF);
                    ps.setInt(9, snapshot.ir() & 0xFF);
                    ps.setInt(10, snapshot.outReg() & 0xFF);
                    ps.setInt(11, snapshot.zeroFlag() ? 1 : 0);
                    ps.setInt(12, snapshot.carryFlag() ? 1 : 0);
                    ps.setInt(13, Math.clamp(snapshot.tState(), 0, 5));
                    ps.setInt(14, snapshot.cycleCount());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public Optional<WorkspaceSnapshot> findByName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT snapshot_id, name, created_at, assembly_source, ram_blob,
                       reg_a, reg_b, pc, mar, ir, out_reg,
                       zero_flag, carry_flag, t_state, cycle_count
                FROM workspace_snapshots
                WHERE name = ?
                """;
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSnapshotRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<WorkspaceSnapshot> listSnapshots() throws SQLException {
        List<WorkspaceSnapshot> list = new ArrayList<>();
        String sql = """
                SELECT snapshot_id, name, created_at, assembly_source, ram_blob,
                       reg_a, reg_b, pc, mar, ir, out_reg,
                       zero_flag, carry_flag, t_state, cycle_count
                FROM workspace_snapshots
                ORDER BY created_at DESC, snapshot_id DESC
                """;
        try (Connection conn = dbManager.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapSnapshotRow(rs));
            }
        }
        return list;
    }

    public boolean deleteSnapshot(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return false;
        }
        String sql = "DELETE FROM workspace_snapshots WHERE name = ?;";
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            return ps.executeUpdate() > 0;
        }
    }

    public void renameSnapshot(String oldName, String newName) throws SQLException {
        if (oldName == null || oldName.isBlank()) {
            throw new IllegalArgumentException("Old snapshot name must not be blank");
        }
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New snapshot name must not be blank");
        }
        String cleanOld = oldName.trim();
        String cleanNew = newName.trim();
        if (cleanOld.equals(cleanNew)) {
            return;
        }
        if (existsByName(cleanNew)) {
            throw new IllegalArgumentException("Snapshot '" + cleanNew + "' already exists");
        }
        if (!existsByName(cleanOld)) {
            throw new IllegalArgumentException("Snapshot '" + cleanOld + "' does not exist");
        }
        String sql = "UPDATE workspace_snapshots SET name = ? WHERE name = ?;";
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanNew);
            ps.setString(2, cleanOld);
            ps.executeUpdate();
        }
    }

    public WorkspaceSnapshot captureFromCpu(String name, String assemblySource, CpuCore cpu) {
        Objects.requireNonNull(cpu, "cpu must not be null");
        synchronized (cpu) {
            return new WorkspaceSnapshot(
                    0L,
                    name,
                    Instant.now().toString(),
                    assemblySource == null ? "" : assemblySource,
                    cpu.copyRam(),
                    cpu.getRegAUnsigned(),
                    cpu.getRegBUnsigned(),
                    cpu.getPcUnsigned(),
                    cpu.getMarUnsigned(),
                    cpu.getIrUnsigned(),
                    cpu.getOutRegUnsigned(),
                    cpu.isZeroFlag(),
                    cpu.isCarryFlag(),
                    cpu.getTState(),
                    cpu.getCycleCount()
            );
        }
    }

    public void restoreIntoCpu(WorkspaceSnapshot snapshot, CpuCore cpu) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(cpu, "cpu must not be null");
        synchronized (cpu) {
            cpu.wipeCleanSlate();
            cpu.setRegA(snapshot.regA());
            cpu.setRegB(snapshot.regB());
            cpu.setPc(snapshot.pc());
            cpu.setMar(snapshot.mar());
            cpu.setIr(snapshot.ir());
            cpu.setOutReg(snapshot.outReg());
            cpu.setZeroFlag(snapshot.zeroFlag());
            cpu.setCarryFlag(snapshot.carryFlag());
            cpu.setTState(snapshot.tState());
            cpu.setCycleCount(snapshot.cycleCount());
            cpu.setHalted(false);
            cpu.loadProgramBytes(0, snapshot.ramBlob());
        }
    }

    public boolean isOnboardingHidden() throws SQLException {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?;";
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, KEY_HIDE_ONBOARDING);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "1".equals(rs.getString("setting_value"));
                }
            }
        }
        return false;
    }

    public void setOnboardingHidden(boolean hidden) throws SQLException {
        String sql = """
                INSERT INTO app_settings (setting_key, setting_value)
                VALUES (?, ?)
                ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value
                """;
        try (Connection conn = dbManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, KEY_HIDE_ONBOARDING);
            ps.setString(2, hidden ? "1" : "0");
            ps.executeUpdate();
        }
    }

    private WorkspaceSnapshot mapSnapshotRow(ResultSet rs) throws SQLException {
        return new WorkspaceSnapshot(
                rs.getLong("snapshot_id"),
                rs.getString("name"),
                rs.getString("created_at"),
                rs.getString("assembly_source"),
                rs.getBytes("ram_blob"),
                rs.getInt("reg_a") & 0xFF,
                rs.getInt("reg_b") & 0xFF,
                rs.getInt("pc") & 0xFF,
                rs.getInt("mar") & 0xFF,
                rs.getInt("ir") & 0xFF,
                rs.getInt("out_reg") & 0xFF,
                rs.getInt("zero_flag") != 0,
                rs.getInt("carry_flag") != 0,
                rs.getInt("t_state"),
                rs.getInt("cycle_count")
        );
    }
}
