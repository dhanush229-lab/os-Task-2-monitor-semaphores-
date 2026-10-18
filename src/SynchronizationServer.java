import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Four original thread-synchronization demonstrations with a live JSONL event stream. */
public class SynchronizationServer {
    static final int PORT = 8765, N = 4;
    static final Object RUN_GATE = new Object();
    static boolean running = false;

    interface Logger { void log(String actor, String state, String detail); }

    static void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
    static void waitThreads(List<Thread> threads) throws InterruptedException {
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
    }
    static void philosophersSemaphore(Logger out) throws InterruptedException {
        Semaphore[] forks = new Semaphore[N];
        for (int i=0;i<N;i++) forks[i]=new Semaphore(1,true);
        List<Thread> threads=new ArrayList<>();
        for (int id=0;id<N;id++) {
            final int p=id;
            threads.add(new Thread(()-> {
                int left=p, right=(p+1)%N;
                int first=p%2==0?left:right, second=p%2==0?right:left;
                for(int round=1;round<=3;round++) {
                    out.log("P"+p,"thinking","round="+round);
                    pause(65 + p*17);
                    out.log("P"+p,"hungry","waiting for chopsticks");
                    boolean a=false,b=false;
                    try {
                        forks[first].acquire(); a=true;
                        out.log("F"+first,"held","P"+p);
                        forks[second].acquire(); b=true;
                        out.log("F"+second,"held","P"+p);
                        out.log("P"+p,"eating","round="+round);
                        pause(105);
                    } catch(InterruptedException e) {Thread.currentThread().interrupt(); return;}
                    finally {
                        out.log("P"+p,"releasing","returning chopsticks");
                        if(b) {forks[second].release();out.log("F"+second,"free","P"+p);}
                        if(a) {forks[first].release();out.log("F"+first,"free","P"+p);}
                    }
                    out.log("P"+p,"finished","round="+round);
                }
            },"philosopher-"+p));
        }
        waitThreads(threads);
    }
    static class ForkMonitor {
        final boolean[] inUse=new boolean[N];
        synchronized void acquire(int p,Logger out) throws InterruptedException {
            int a=p,b=(p+1)%N;
            while(inUse[a]||inUse[b]) wait();
            inUse[a]=true; inUse[b]=true;
            out.log("F"+a,"held","P"+p);
            out.log("F"+b,"held","P"+p);
        }
        synchronized void release(int p,Logger out) {
            int a=p,b=(p+1)%N;
            inUse[a]=false;inUse[b]=false;
            out.log("F"+a,"free","P"+p);
            out.log("F"+b,"free","P"+p);
            notifyAll();
        }
    }
    static void philosophersMonitor(Logger out) throws InterruptedException {
        ForkMonitor monitor=new ForkMonitor();
        List<Thread> threads=new ArrayList<>();
        for(int id=0;id<N;id++) {
            final int p=id;
            threads.add(new Thread(()-> {
                for(int round=1;round<=3;round++) {
                    out.log("P"+p,"thinking","round="+round); pause(65+p*17);
                    out.log("P"+p,"hungry","atomic pair requested");
                    try {monitor.acquire(p,out);} catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                    try {out.log("P"+p,"eating","round="+round);pause(105);}
                    finally {out.log("P"+p,"releasing","returning chopsticks");monitor.release(p,out);}
                    out.log("P"+p,"finished","round="+round);
                }
            },"philosopher-"+p));
        }
        waitThreads(threads);
    }
    static void readersWritersSemaphore(Logger out) throws InterruptedException {
        final Semaphore turnstile=new Semaphore(1,true), roomEmpty=new Semaphore(1,true), countLock=new Semaphore(1,true);
        final AtomicInteger count=new AtomicInteger(0), value=new AtomicInteger(10);
        final List<Thread> ts=new ArrayList<>();
        for(int id=0;id<5;id++) {
            final int p=id;
            ts.add(new Thread(()-> {
                pause(p*35);out.log("R"+p,"waiting","request access");
                boolean entered=false;
                try {
                    turnstile.acquire(); turnstile.release();
                    countLock.acquire();
                    try {if(count.incrementAndGet()==1) roomEmpty.acquire();entered=true;}
                    finally {countLock.release();}
                    out.log("R"+p,"reading","value="+value.get());pause(135);
                } catch(InterruptedException e){Thread.currentThread().interrupt();}
                finally {if(entered) {
                    out.log("R"+p,"leaving","reader leaving");
                    countLock.acquireUninterruptibly();
                    try {if(count.decrementAndGet()==0) roomEmpty.release();}
                    finally {countLock.release();}
                    out.log("R"+p,"finished","read complete");
                }}
            },"reader-"+p));
        }
        for(int id=0;id<3;id++) {
            final int p=id;
            ts.add(new Thread(()-> {
                pause(50+p*65);out.log("W"+p,"waiting","request exclusive write");
                boolean gate=false,room=false;
                try {
                    turnstile.acquire();gate=true;
                    roomEmpty.acquire();room=true;
                    int next=value.incrementAndGet();
                    out.log("W"+p,"writing","value="+next);pause(145);
                } catch(InterruptedException e) {Thread.currentThread().interrupt();}
                finally {
                    if(room)out.log("W"+p,"leaving","writer leaving");
                    if(room)roomEmpty.release();
                    if(gate)turnstile.release();
                    if(room)out.log("W"+p,"finished","write complete");
                }
            },"writer-"+p));
        }
        waitThreads(ts);
    }
    static class QueueMonitor {
        final Deque<String> queue = new ArrayDeque<>();
        int readers=0,value=10;boolean writer=false;
        synchronized void enterReader(String id,Logger out) throws InterruptedException {
            queue.addLast(id);
            try {
                while(writer || hasWriterAhead(id))wait();
                queue.remove(id);readers++;
                out.log(id,"reading","value="+value);
                notifyAll();
            }catch(InterruptedException ex){queue.remove(id);notifyAll();throw ex;}
        }
        boolean hasWriterAhead(String id) {
            for(String req:queue) {if(req.equals(id))return false;if(req.startsWith("W"))return true;}
            return false;
        }
        synchronized void leaveReader(String id,Logger out){readers--;out.log(id,"finished","read complete");notifyAll();}
        synchronized void enterWriter(String id,Logger out) throws InterruptedException {
            queue.addLast(id);
            try {
                while(writer||readers>0||!id.equals(queue.peekFirst())) wait();
                queue.removeFirst();writer=true;
                value++;
                out.log(id,"writing","value="+value);
            }catch(InterruptedException ex){queue.remove(id);notifyAll();throw ex;}
        }
        synchronized void leaveWriter(String id,Logger out){writer=false;out.log(id,"finished","write complete");notifyAll();}
    }
    static void readersWritersMonitor(Logger out) throws InterruptedException {
        QueueMonitor monitor=new QueueMonitor();
        List<Thread> ts=new ArrayList<>();
        for(int id=0;id<5;id++){
            final int p=id;
            ts.add(new Thread(()->{
                pause(p*35);String name="R"+p;out.log(name,"waiting","queued for read");
                try{monitor.enterReader(name,out);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                try{pause(135);}finally{monitor.leaveReader(name,out);}
            },"reader-"+p));
        }
        for(int id=0;id<3;id++){
            final int p=id;
            ts.add(new Thread(()->{
                pause(50+p*65);String name="W"+p;out.log(name,"waiting","queued for write");
                try{monitor.enterWriter(name,out);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                try{pause(145);}finally{monitor.leaveWriter(name,out);}
            },"writer-"+p));
        }
        waitThreads(ts);
    }
    static String json(String s) {
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
    }
    static void serveRun(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.getResponseHeaders().set("Content-Type","application/x-ndjson; charset=utf-8");
        if(!ex.getRequestMethod().equals("POST")){ex.sendResponseHeaders(405,-1);return;}
        String mode=Optional.ofNullable(ex.getRequestURI().getQuery()).orElse("").replace("mode=","");
        if(!List.of("philosophers-semaphore","philosophers-monitor","rw-semaphore","rw-monitor").contains(mode)){
            ex.sendResponseHeaders(400,-1);return;
        }
        synchronized(RUN_GATE) {
            if(running){ex.sendResponseHeaders(409,-1);return;}
            running=true;
        }
        ex.sendResponseHeaders(200,0);
        try(OutputStream stream=ex.getResponseBody()){
            long start=System.nanoTime();
            AtomicInteger sequence=new AtomicInteger();
            Logger out=(actor,state,detail)->{
                synchronized(stream){
                    int num=sequence.incrementAndGet();
                    long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                    String line="{\"seq\":"+num+",\"ms\":"+millis+",\"actor\":"+json(actor)+",\"state\":"+json(state)+",\"detail\":"+json(detail)+"}\n";
                    try {stream.write(line.getBytes(StandardCharsets.UTF_8));stream.flush();}catch(IOException io){ /* client disconnected; workers finish */ }
                }
            };
            out.log("SYSTEM","started",mode);
            try{
                switch(mode){
                    case "philosophers-semaphore"->philosophersSemaphore(out);
                    case "philosophers-monitor"->philosophersMonitor(out);
                    case "rw-semaphore"->readersWritersSemaphore(out);
                    case "rw-monitor"->readersWritersMonitor(out);
                }
                out.log("SYSTEM","complete","simulation finished");
            }catch(InterruptedException e){Thread.currentThread().interrupt();out.log("SYSTEM","error","interrupted");}
        }finally{synchronized(RUN_GATE){running=false;}}
    }
    static void serveStatic(HttpExchange ex) throws IOException {
        String uri=ex.getRequestURI().getPath();
        Path root=Path.of("web").toAbsolutePath().normalize();
        Path file=root.resolve(uri.equals("/")?"index.html":uri.substring(1)).normalize();
        if(!file.startsWith(root)||!Files.isRegularFile(file)){ex.sendResponseHeaders(404,-1);return;}
        String type=uri.endsWith(".css")?"text/css":uri.endsWith(".js")?"application/javascript":"text/html";
        ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        byte[] data=Files.readAllBytes(file);
        ex.sendResponseHeaders(200,data.length);
        try(OutputStream output=ex.getResponseBody()){output.write(data);}
    }
    public static void main(String[] args) throws IOException {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",PORT),0);
        server.createContext("/api/run",SynchronizationServer::serveRun);
        server.createContext("/",SynchronizationServer::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Open http://localhost:"+PORT+" in your browser");
    }
}
