package org.bdj.external;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.bdj.Status;
import org.bdj.api.*;

public class ElfLoader {

    private static final long MAPPING_ADDR        = 0x926100000L;
    private static final long SHADOW_MAPPING_ADDR = 0x920100000L;

    private static final int PROT_READ  = 0x1;
    private static final int PROT_WRITE = 0x2;
    private static final int PROT_EXEC  = 0x4;

    private static final int MAP_SHARED    = 0x0001;
    private static final int MAP_PRIVATE   = 0x0002;
    private static final int MAP_FIXED     = 0x0010;
    private static final int MAP_ANONYMOUS = 0x1000;

    private static final int ELF_MAGIC         = 0x464C457F;
    private static final int PT_LOAD           = 1;
    private static final int SHT_RELA          = 4;
    private static final int R_X86_64_RELATIVE = 0x08;

    private static final int FILEDESCENT_SIZE = 0x30;

    private static final int NETWORK_PORT     = 9020;
    private static final int READ_CHUNK_SIZE  = 4096;
    private static final int MAX_PAYLOAD_SIZE = 4 * 1024 * 1024;

    private static final int AF_INET6      = 28;
    private static final int IPPROTO_IPV6  = 41;
    private static final int INPCB_PKTOPTS = 0x120;

    private static final API api;
    private static final KernelAPI kapi;

    private static final long getpid;
    private static final long mmap;
    private static final long munmap;
    private static final long pipe;
    private static final long sched_yield;
    private static final long socket;
    private static final long setsockopt;
    private static final long sceKernelJitCreateSharedMemory;
    private static final long sceKernelJitCreateAliasOfSharedMemory;
    private static final long scePthreadCreate;
    private static final long scePthreadJoin;
    private static final long scePthreadAttrInit;
    private static final long scePthreadAttrSetstacksize;
    private static final long scePthreadAttrSetdetachstate;
    private static final long scePthreadAttrDestroy;

    private static long kdata_base;
    private static long kq_fdp;
    
    private static String embed_elf_path = "/org/bdj/external/elfldr_1320.elf";
    
    static {
        try {
            api  = API.getInstance();
            kapi = KernelAPI.getInstance();

            getpid                                = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "getpid");
            mmap                                  = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "mmap");
            munmap                                = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "munmap");
            pipe                                  = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "pipe");
            sched_yield                           = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sched_yield");
            socket                                = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "socket");
            setsockopt                            = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "setsockopt");
            sceKernelJitCreateSharedMemory        = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelJitCreateSharedMemory");
            sceKernelJitCreateAliasOfSharedMemory = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelJitCreateAliasOfSharedMemory");
            scePthreadCreate                      = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadCreate");
            scePthreadJoin                        = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadJoin");
            scePthreadAttrInit                    = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadAttrInit");
            scePthreadAttrSetstacksize            = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadAttrSetstacksize");
            scePthreadAttrSetdetachstate          = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadAttrSetdetachstate");
            scePthreadAttrDestroy                 = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "scePthreadAttrDestroy");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static class ElfHeader {
        long entry;
        long phOff;
        long shOff;
        int  phEntSize;
        int  phNum;
        int  shEntSize;
        int  shNum;
    }

    private static class ProgramHeader {
        int  type;
        int  flags;
        long offset;
        long vAddr;
        long fileSize;
        long memSize;
    }

    private static class SectionHeader {
        int  type;
        long offset;
        long size;
    }

    private static void ensureAddresses() {
        if (kdata_base == 0 || kq_fdp == 0) {
            kdata_base = kapi.getKdataBase();
            kq_fdp = kapi.getKqFdp();
        }

        if (kdata_base == 0 || kq_fdp == 0) {
            throw new IllegalStateException("System not jailbroken (Kernel addresses not found)");
        }
    }

    private static long elfParse(byte[] elfData) throws Exception {
        if (elfData.length < 4) {
            throw new IllegalArgumentException("ELF too small");
        }
        if (readLE32(elfData, 0) != ELF_MAGIC) {
            throw new IllegalArgumentException("Not a valid ELF file (bad magic)");
        }

        ElfHeader eh = readElfHeader(elfData);

        long execSegStart = 0;
        long execSegEnd   = 0;

        for (int i = 0; i < eh.phNum; i++) {
            int phBase = (int)(eh.phOff + (long) i * eh.phEntSize);
            ProgramHeader ph = readProgramHeader(elfData, phBase);

            if (ph.type != PT_LOAD || ph.memSize == 0) continue;

            long alignedSize = (ph.memSize + 0x3FFFL) & 0xFFFFC000L;
            long destAddr    = MAPPING_ADDR + ph.vAddr;
            boolean isExec   = (ph.flags & 0x1) != 0;

            if (isExec) {
                execSegStart = ph.vAddr;
                execSegEnd   = ph.vAddr + ph.memSize;

                Int32 fdBuf    = new Int32();
                long retCreate = api.call(sceKernelJitCreateSharedMemory, 0L, alignedSize, 7L, fdBuf.address());
                int  execFd    = fdBuf.get();
                if (retCreate != 0 || execFd <= 0) {
                    throw new RuntimeException(
                        "sceKernelJitCreateSharedMemory failed: ret=" + String.valueOf(retCreate) + " fd=" + String.valueOf(execFd));
                }

                long retAlias = api.call(sceKernelJitCreateAliasOfSharedMemory, (long) execFd, 3L, fdBuf.address());
                int  writeFd  = fdBuf.get();
                if (retAlias != 0 || writeFd <= 0) {
                    throw new RuntimeException(
                        "sceKernelJitCreateAliasOfSharedMemory failed: ret=" + String.valueOf(retAlias) + " fd=" + String.valueOf(writeFd));
                }

                long shadowResult = api.call(mmap,
                    SHADOW_MAPPING_ADDR, alignedSize,
                    (long)(PROT_READ | PROT_WRITE),
                    (long)(MAP_SHARED | MAP_FIXED),
                    (long) writeFd, 0L);
                if (shadowResult != SHADOW_MAPPING_ADDR) {
                    throw new RuntimeException(
                        "Shadow mmap failed: 0x" + Long.toHexString(shadowResult));
                }

                if (ph.fileSize > 0) {
                    byte[] segData = new byte[(int) ph.fileSize];
                    System.arraycopy(elfData, (int) ph.offset, segData, 0, (int) ph.fileSize);
                    api.memcpy(SHADOW_MAPPING_ADDR, segData, segData.length);
                }
                if (ph.memSize > ph.fileSize) {
                    api.memset(SHADOW_MAPPING_ADDR + ph.fileSize, 0, (int)(ph.memSize - ph.fileSize));
                }

                long execResult = api.call(mmap,
                    destAddr, alignedSize,
                    (long)(PROT_READ | PROT_EXEC),
                    (long)(MAP_SHARED | MAP_FIXED),
                    (long) execFd, 0L);
                if (execResult != destAddr) {
                    throw new RuntimeException(
                        "Exec mmap failed: 0x" + Long.toHexString(execResult));
                }

                // Status.println("Mapped exec segment @ 0x" + Long.toHexString(destAddr) +
                //                " (size: 0x" + Long.toHexString(alignedSize) + ")");

            } else {
                long dataResult = api.call(mmap,
                    destAddr, alignedSize,
                    (long)(PROT_READ | PROT_WRITE),
                    (long)(MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED),
                    -1L, 0L);
                if (dataResult != destAddr) {
                    throw new RuntimeException(
                        "Data mmap failed: 0x" + Long.toHexString(dataResult));
                }

                if (ph.fileSize > 0) {
                    byte[] segData = new byte[(int) ph.fileSize];
                    System.arraycopy(elfData, (int) ph.offset, segData, 0, (int) ph.fileSize);
                    api.memcpy(destAddr, segData, segData.length);
                }

                // Status.println("Mapped data segment @ 0x" + Long.toHexString(destAddr) +
                //                " (size: 0x" + Long.toHexString(alignedSize) + ")");
            }
        }

        for (int i = 0; i < eh.shNum; i++) {
            int shBase = (int)(eh.shOff + (long) i * eh.shEntSize);
            SectionHeader sh = readSectionHeader(elfData, shBase);

            if (sh.type != SHT_RELA) continue;

            long relaCount = sh.size / 0x18L;
            for (long j = 0; j < relaCount; j++) {
                int relaBase = (int)(sh.offset + j * 0x18L);
                long rOffset = readLE64(elfData, relaBase + 0x00);
                long rInfo   = readLE64(elfData, relaBase + 0x08);
                long rAddend = readLE64(elfData, relaBase + 0x10);

                if ((rInfo & 0xFFL) != R_X86_64_RELATIVE) continue;

                long writeTarget = (rOffset >= execSegStart && rOffset < execSegEnd)
                    ? SHADOW_MAPPING_ADDR + rOffset
                    : MAPPING_ADDR        + rOffset;

                api.write64(writeTarget, MAPPING_ADDR + rAddend);
            }
        }

        long elfEntryPoint = MAPPING_ADDR + eh.entry;
        // Status.println("ELF entry point: 0x" + Long.toHexString(elfEntryPoint));
        return elfEntryPoint;
    }

    private static long getFdtOfiles() {
        return kapi.kread64(kq_fdp) + 0x8;
    }

    private static boolean looksKernelPtr(long value) {
        return value != 0 && (value & 0xFFFF000000000000L) == 0xFFFF000000000000L;
    }
    
    // https://github.com/MassZero0/NetPoops-PS5/blob/main/src/org/homebrew/Poops.java
    private static long[] createElfPipes() {
        long fdt_ofiles = getFdtOfiles();
        Int32Array pipeFd = new Int32Array(2);
        if ((int) api.call(pipe, pipeFd.address()) != 0) {
            return null;
        }
    
        int rfd = pipeFd.get(0);
        int wfd = pipeFd.get(1);
        
        long prfp = 0;
        for (int i = 0; i < 100; i++) {
            prfp = kapi.kread64(fdt_ofiles + rfd * FILEDESCENT_SIZE);
            if (prfp != 0) break;
            api.call(sched_yield);
        }
        if (prfp == 0) {
            return null;
        }
    
        long kpipe = kapi.kread64(prfp);
        if (kpipe == 0) {
            return null;
        }
    
        long pwfp = 0;
        for (int i = 0; i < 100; i++) {
            pwfp = kapi.kread64(fdt_ofiles + wfd * FILEDESCENT_SIZE);
            if (pwfp != 0) break;
            api.call(sched_yield);
        }
        if (pwfp == 0) {
            return null;
        }
    
        kapi.kwrite32(prfp + 0x28, kapi.kread32(prfp + 0x28) + 1);
        kapi.kwrite32(pwfp + 0x28, kapi.kread32(pwfp + 0x28) + 1);
        return new long[]{ rfd, wfd, kpipe };
    }
    
    private static long[] createOverlappedSockets() {
        long fdt_ofiles = getFdtOfiles();
        int ms = (int) api.call(socket, AF_INET6, 2, 17);
        int vs = (int) api.call(socket, AF_INET6, 2, 17);
        if (ms < 0 || vs < 0) return null;
    
        Buffer mbuf = new Buffer(20);
        Buffer vbuf = new Buffer(20);
        api.call(setsockopt, ms, IPPROTO_IPV6, 46, mbuf.address(), 20);
        api.call(setsockopt, vs, IPPROTO_IPV6, 46, vbuf.address(), 20);
    
        long mfp = 0;
        long vfp = 0;
        for (int i = 0; i < 100; i++) {
            if (mfp == 0) mfp = kapi.kread64(fdt_ofiles + ms * FILEDESCENT_SIZE);
            if (vfp == 0) vfp = kapi.kread64(fdt_ofiles + vs * FILEDESCENT_SIZE);
            if (mfp != 0 && vfp != 0) break;
            api.call(sched_yield);
        }
        if (mfp == 0 || vfp == 0) return null;
    
        long mso  = kapi.kread64(mfp);
        long vso  = kapi.kread64(vfp);
        long mpcb = kapi.kread64(mso + 0x18);
        long vpcb = kapi.kread64(vso + 0x18);
        long mp   = kapi.kread64(mpcb + INPCB_PKTOPTS);
        long vp   = kapi.kread64(vpcb + INPCB_PKTOPTS);
        if (mp == 0 || vp == 0) return null;
    
        if (!looksKernelPtr(mp) || !looksKernelPtr(vp)) return null;
    
        kapi.kwrite32(mfp + 0x28, kapi.kread32(mfp + 0x28) + 1);
        kapi.kwrite32(vfp + 0x28, kapi.kread32(vfp + 0x28) + 1);
        kapi.kwrite64(mp + 0x10, vp + 0x10);
        return new long[]{ ms, vs };
    }

    private static long elfRun(long elfEntryPoint, Int32 payloadout) throws Exception {
        long[] pipeData   = createElfPipes();
        long[] socketData = createOverlappedSockets();
        if (pipeData == null || socketData == null) {
            throw new RuntimeException("failed to create pipe/socket environment");
        }
    
        Buffer rwpipe    = new Buffer(8);
        Buffer rwpair    = new Buffer(8);
        Buffer args      = new Buffer(0x30);
        Int64  thrHandle = new Int64();
        Buffer attr      = new Buffer(0x100);
        Text   name      = new Text("elfldr");
        
        rwpipe.putInt(0x00, (int) pipeData[0]);
        rwpipe.putInt(0x04, (int) pipeData[1]);
    
        rwpair.putInt(0x00, (int) socketData[0]);
        rwpair.putInt(0x04, (int) socketData[1]);
    
        args.putLong(0x00, getpid);           
        args.putLong(0x08, rwpipe.address()); 
        args.putLong(0x10, rwpair.address()); 
        args.putLong(0x18, pipeData[2]);      
        args.putLong(0x20, kdata_base);       
        args.putLong(0x28, payloadout.address());
        
        // Status.println("Spawning ELF thread at: 0x" + Long.toHexString(elfEntryPoint));
        
        api.call(scePthreadAttrInit,           attr.address());
        api.call(scePthreadAttrSetstacksize,   attr.address(), 0x80000L);
        api.call(scePthreadAttrSetdetachstate, attr.address(), 0L);
        
        long ret = api.call(scePthreadCreate, thrHandle.address(), attr.address(), elfEntryPoint, args.address(), name.address());
        api.call(scePthreadAttrDestroy, attr.address());
        
        if (ret != 0) {
            throw new RuntimeException("scePthreadCreate failed: " + String.valueOf(ret));
        }
        
        // Status.println("ELF thread spawned, handle: 0x" + Long.toHexString(thrHandle.get()));
        return thrHandle.get();
    }

    private static void elfLoader(byte[] elfData) throws Exception {
        Status.println("Loading elfldr");
        Int32 payloadout   = new Int32();
        long elfEntryPoint = elfParse(elfData);
        elfRun(elfEntryPoint, payloadout);
        // Status.println("done");
    }

    public static void loadEmbeddedElf() {
        InputStream elfStream = ElfLoader.class.getResourceAsStream(embed_elf_path);
        if (elfStream == null) {
            Status.println("Embedded ELF not found in jar: " + embed_elf_path);
            return;
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_CHUNK_SIZE];
            int n;
            while ((n = elfStream.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            byte[] elfData = buf.toByteArray();
            buf.close();
            // Status.println("Embedded ELF size: " + String.valueOf(elfData.length) +
            //             " bytes (0x" + Integer.toHexString(elfData.length) + ")");
            loadElf(elfData);
        } catch (Exception e) {
            Status.printStackTrace("Failed to load embedded ELF: ", e);
        } finally {
            try { elfStream.close(); } catch (IOException ignored) {}
        }
    }

    public static void loadElf(byte[] elfData) {
        try {
            ensureAddresses();
            elfLoader(elfData);
        } catch (Exception e) {
            Status.printStackTrace("Failed to load ELF: ", e);
        }
    }

    private static void listenForPayloadsOnPort(int port) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            Status.println("ElfLoader listening on port " + String.valueOf(port));

            while (true) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    Status.println("Connection from: " + String.valueOf(clientSocket.getInetAddress()));

                    byte[] payload = readPayloadFromSocket(clientSocket);
                    Status.println("Received ELF payload: " + String.valueOf(payload.length) +
                                   " bytes (0x" + Integer.toHexString(payload.length) + ")");

                    elfLoader(payload);

                    Status.println("ElfLoader listening on port " + String.valueOf(port));

                } catch (Exception e) {
                    Status.printStackTrace("Error processing ELF payload: ", e);
                } finally {
                    if (clientSocket != null) {
                        try { clientSocket.close(); } catch (IOException ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            Status.printStackTrace("ElfLoader server error: ", e);
        } finally {
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static byte[] readPayloadFromSocket(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[READ_CHUNK_SIZE];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_PAYLOAD_SIZE) {
                throw new IOException("ELF payload too large: " + String.valueOf(out.size()) + " bytes");
            }
        }
        byte[] data = out.toByteArray();
        out.close();
        if (data.length == 0) throw new IOException("No ELF data received");
        return data;
    }

    private static ElfHeader readElfHeader(byte[] d) {
        ElfHeader h = new ElfHeader();
        h.entry     = readLE64(d, 0x18);
        h.phOff     = readLE64(d, 0x20);
        h.shOff     = readLE64(d, 0x28);
        h.phEntSize = readLE16(d, 0x36) & 0xFFFF;
        h.phNum     = readLE16(d, 0x38) & 0xFFFF;
        h.shEntSize = readLE16(d, 0x3A) & 0xFFFF;
        h.shNum     = readLE16(d, 0x3C) & 0xFFFF;
        return h;
    }

    private static ProgramHeader readProgramHeader(byte[] d, int base) {
        ProgramHeader ph = new ProgramHeader();
        ph.type     = readLE32(d, base + 0x00);
        ph.flags    = readLE32(d, base + 0x04);
        ph.offset   = readLE64(d, base + 0x08);
        ph.vAddr    = readLE64(d, base + 0x10);
        ph.fileSize = readLE64(d, base + 0x20);
        ph.memSize  = readLE64(d, base + 0x28);
        return ph;
    }

    private static SectionHeader readSectionHeader(byte[] d, int base) {
        SectionHeader sh = new SectionHeader();
        sh.type   = readLE32(d, base + 0x04);
        sh.offset = readLE64(d, base + 0x18);
        sh.size   = readLE64(d, base + 0x20);
        return sh;
    }

    private static int readLE16(byte[] d, int off) {
        return ((d[off + 1] & 0xFF) << 8) | (d[off] & 0xFF);
    }

    private static int readLE32(byte[] d, int off) {
        return ((d[off + 3] & 0xFF) << 24) | ((d[off + 2] & 0xFF) << 16)
             | ((d[off + 1] & 0xFF) <<  8) |  (d[off]     & 0xFF);
    }

    private static long readLE64(byte[] d, int off) {
        return ((long)(d[off + 7] & 0xFF) << 56) | ((long)(d[off + 6] & 0xFF) << 48)
             | ((long)(d[off + 5] & 0xFF) << 40) | ((long)(d[off + 4] & 0xFF) << 32)
             | ((long)(d[off + 3] & 0xFF) << 24) | ((long)(d[off + 2] & 0xFF) << 16)
             | ((long)(d[off + 1] & 0xFF) <<  8) |  (long)(d[off]     & 0xFF);
    }
}