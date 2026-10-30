package dev.frtzhahn.micro8.db;

import dev.frtzhahn.micro8.cpu.CpuCore;
import dev.frtzhahn.micro8.ui.OnboardingController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotAndOnboardingTest {

    @Test
    void loadsAndInstantiatesPatchedTextFlowExtOnJavaFx25() throws Exception {
        Class<?> cls = Class.forName("org.fxmisc.richtext.TextFlowExt");
        Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void capturesSavesRestoresAndManagesOverwriteAndOnboardingSettings(@TempDir Path tempDir) throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("micro8-m8.db"));
        IsaRepository isaRepo = new IsaRepository(dbManager);
        isaRepo.initializeSchemaAndSeed();

        SnapshotRepository snapshotRepo = new SnapshotRepository(dbManager);

        assertFalse(snapshotRepo.isOnboardingHidden());
        snapshotRepo.setOnboardingHidden(true);
        assertTrue(snapshotRepo.isOnboardingHidden());
        snapshotRepo.setOnboardingHidden(false);
        assertFalse(snapshotRepo.isOnboardingHidden());

        assertEquals(4, OnboardingController.SLIDES.size());
        assertEquals(1, OnboardingController.SLIDES.getFirst().slideNumber());
        assertEquals(4, OnboardingController.SLIDES.getLast().slideNumber());

        CpuCore sourceCpu = new CpuCore();
        sourceCpu.writeRam(0x00, 0x11);
        sourceCpu.writeRam(0x01, 0xFE);
        sourceCpu.writeRam(0xF0, 0x80);
        sourceCpu.writeRam(0xFF, 0xFF);
        sourceCpu.setRegA(0xFE);
        sourceCpu.setRegB(0x80);
        sourceCpu.setPc(0x04);
        sourceCpu.setMar(0xF0);
        sourceCpu.setIr(0x20);
        sourceCpu.setOutReg(0xFF);
        sourceCpu.setZeroFlag(true);
        sourceCpu.setCarryFlag(true);
        sourceCpu.setTState(3);
        sourceCpu.setCycleCount(42);

        String asmSource = "START:\n    LDI 0xFE\n    HLT\n";
        WorkspaceSnapshot captured = snapshotRepo.captureFromCpu("lab-checkpoint", asmSource, sourceCpu);
        snapshotRepo.saveSnapshot(captured, false);

        assertTrue(snapshotRepo.existsByName("lab-checkpoint"));
        assertThrows(IllegalStateException.class, () -> snapshotRepo.saveSnapshot(captured, false));

        sourceCpu.setOutReg(0xAA);
        WorkspaceSnapshot updated = snapshotRepo.captureFromCpu("lab-checkpoint", asmSource + "; updated\n", sourceCpu);
        snapshotRepo.saveSnapshot(updated, true);

        Optional<WorkspaceSnapshot> loadedOpt = snapshotRepo.findByName("lab-checkpoint");
        assertTrue(loadedOpt.isPresent());
        WorkspaceSnapshot loaded = loadedOpt.get();
        assertEquals(asmSource + "; updated\n", loaded.assemblySource());
        assertEquals(0xAA, loaded.outReg());

        CpuCore targetCpu = new CpuCore();
        snapshotRepo.restoreIntoCpu(loaded, targetCpu);

        assertArrayEquals(sourceCpu.copyRam(), targetCpu.copyRam());
        assertEquals(0x80, targetCpu.readRamUnsigned(0xF0));
        assertEquals(0xFF, targetCpu.readRamUnsigned(0xFF));
        assertEquals(0xFE, targetCpu.getRegAUnsigned());
        assertEquals(0x80, targetCpu.getRegBUnsigned());
        assertEquals(0x04, targetCpu.getPcUnsigned());
        assertEquals(0xF0, targetCpu.getMarUnsigned());
        assertEquals(0x20, targetCpu.getIrUnsigned());
        assertEquals(0xAA, targetCpu.getOutRegUnsigned());
        assertTrue(targetCpu.isZeroFlag());
        assertTrue(targetCpu.isCarryFlag());
        assertEquals(3, targetCpu.getTState());
        assertEquals(42, targetCpu.getCycleCount());

        List<WorkspaceSnapshot> all = snapshotRepo.listSnapshots();
        assertEquals(1, all.size());

        assertTrue(snapshotRepo.deleteSnapshot("lab-checkpoint"));
        assertFalse(snapshotRepo.existsByName("lab-checkpoint"));
    }

    @Test
    void renamesSnapshotAndRejectsDuplicateOrInvalidNames(@TempDir Path tempDir) throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("micro8-rename.db"));
        IsaRepository isaRepo = new IsaRepository(dbManager);
        isaRepo.initializeSchemaAndSeed();

        SnapshotRepository snapshotRepo = new SnapshotRepository(dbManager);
        CpuCore cpu = new CpuCore();
        cpu.setRegA(42);

        WorkspaceSnapshot alpha = snapshotRepo.captureFromCpu("snap-alpha", "NOP\n", cpu);
        WorkspaceSnapshot beta = snapshotRepo.captureFromCpu("snap-beta", "HLT\n", cpu);
        snapshotRepo.saveSnapshot(alpha, false);
        snapshotRepo.saveSnapshot(beta, false);

        assertTrue(snapshotRepo.existsByName("snap-alpha"));
        assertTrue(snapshotRepo.existsByName("snap-beta"));

        snapshotRepo.renameSnapshot("snap-alpha", "snap-charlie");
        assertFalse(snapshotRepo.existsByName("snap-alpha"));
        assertTrue(snapshotRepo.existsByName("snap-charlie"));

        Optional<WorkspaceSnapshot> charlieOpt = snapshotRepo.findByName("snap-charlie");
        assertTrue(charlieOpt.isPresent());
        assertEquals(42, charlieOpt.get().regA());
        assertEquals("NOP\n", charlieOpt.get().assemblySource());

        assertThrows(IllegalArgumentException.class, () -> snapshotRepo.renameSnapshot("snap-charlie", "snap-beta"));
        assertThrows(IllegalArgumentException.class, () -> snapshotRepo.renameSnapshot("snap-charlie", ""));
        assertThrows(IllegalArgumentException.class, () -> snapshotRepo.renameSnapshot("snap-charlie", "   "));
        assertThrows(IllegalArgumentException.class, () -> snapshotRepo.renameSnapshot("non-existent", "snap-delta"));
    }
}
