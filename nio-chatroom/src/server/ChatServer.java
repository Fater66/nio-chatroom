package server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Set;

public class ChatServer {

    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 102;

    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER);
    private Charset charset = Charset.forName("UTF-8");
    private int port;

    public ChatServer(){
        this(DEFAULT_PORT);
    }
    public ChatServer(int port){
        this.port = port;
    }
    private void start(){
        try {
            server = ServerSocketChannel.open();
            //确保server处于非阻塞状态 设置为false；
            server.configureBlocking(false);
            //将channel与socket绑定
            server.socket().bind(new InetSocketAddress(port));

            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器，监听端口："+port+"...");

            while (true) {
                //select阻塞式调用 直到通道内有注册的事件触发
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys){
                    //处理被触发的事件
                    handles(key);
                }
                selectionKeys.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            close(selector);
        }
    }

    private void handles(SelectionKey key) throws IOException {
        //ACCEPT events - 和客户端建立了连接
        if (key.isAcceptable()){
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            client.register(selector,SelectionKey.OP_READ);
            System.out.println(getClientName(client)+"]已连接");
        }
        //READ events - 客户端发送了消息
        else if (key.isReadable()){
            SocketChannel client = (SocketChannel) key.channel();
            String fwdMsg = receive(client);
            if (fwdMsg.isEmpty()){
                //客户端异常
                key.cancel();
                //监听状态发生了改变
                selector.wakeup();
            }else {
                forwardMessage(client,fwdMsg);

                //检查是否退出
                if(readyToQuit(fwdMsg)){
                    key.cancel();
                    selector.wakeup();
                    System.out.println(getClientName(client) + "已断开 ");
                }
            }
        }
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for (SelectionKey key : selector.keys()){
            Channel connectedClient =  key.channel();
            if (connectedClient instanceof ServerSocketChannel){
                continue;
            }
            if (key.isValid() && !client.equals(connectedClient)){
                writeBuffer.clear();
                writeBuffer.put(charset.encode(getClientName(client) + ":" +fwdMsg));
                writeBuffer.flip();
                while (writeBuffer.hasRemaining()){
                    ((SocketChannel)connectedClient).write(writeBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        readBuffer.clear();
        while(client.read(readBuffer) > 0);
        readBuffer.flip();
        return String.valueOf(charset.decode(readBuffer));
    }

    private  String getClientName(SocketChannel client){
        return "客户端["+client.socket().getPort();
    }
    private void close(Closeable closeable) {
        if(closeable!= null){
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    public static void main(String[] args){
        ChatServer chatServer = new ChatServer(7777);
        chatServer.start();
    }

}
