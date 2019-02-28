import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;


public class IlsHttpServer extends Thread{
    static Queue<HashMap> untreatedRequestsQueue = new ArrayDeque<>();

    IlsHttpServer(int port){
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            HttpContext context = server.createContext("/pana");
            context.setHandler(IlsHttpServer::handleRequest);
            server.start();
        } catch(IOException e){
            e.printStackTrace();
        }
        System.out.println("HTTP SERVER IS LISTENING...");
    }

    private static void handleRequest(HttpExchange exchange){
        HashMap<String, Integer> req = new HashMap<String, Integer>();
        try{
            // リクエストのクエリを処理待ちキューに追加
            String query = exchange.getRequestURI().getQuery();
            String[] tmp = query.split("&");
            if(tmp.length != 3){
                String response = "invalid query.";
                exchange.sendResponseHeaders(300, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }
            req.put(tmp[0].split("=")[0], Integer.parseInt(tmp[0].split("=")[1]));
            req.put(tmp[1].split("=")[0], Integer.parseInt(tmp[1].split("=")[1]));
            req.put(tmp[2].split("=")[0], Integer.parseInt(tmp[2].split("=")[1]));
            untreatedRequestsQueue.add(req);
            //System.out.println(req);

            // レスポンスを作成、送信
            String response = "accepted.";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    // なんかやるかも
    @Override
    public void run(){
        while(true) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap getSingleRequest(){
        HashMap toq = untreatedRequestsQueue.poll();
        if (toq == null) {
            return null;
        }
        return toq;
    }
}
