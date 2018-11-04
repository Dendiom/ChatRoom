package com.lucky;

import com.google.gson.Gson;
import com.lucky.bean.Room;
import com.lucky.constant.ErrorMsg;
import com.lucky.constant.MsgType;
import com.lucky.thread.ServerSocketThread;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class);

    private int port;
    private boolean alive;
    private AtomicInteger count = new AtomicInteger(0);  // 当前连接的socket数目
    private Map<String, List<ServerSocketThread>> rooms =
            new HashMap<String, List<ServerSocketThread>>(); // 当前的所有socket线程与聊天室, 操作需要同步
    private Gson gson = new Gson();
    private ServerSocket server;

    public Server() {
        initFromProperties();
        addShutdownHook();
    }

    public void start() {
        try {
            server = new ServerSocket(port);
            alive = true;
            logger.info("SocketServer has started, listening port: " + port);
            while (alive) {
                Socket socket = server.accept();
                Thread socketThread = new ServerSocketThread(socket, this);
                socketThread.start();
                count.incrementAndGet();
                logger.info("Welcome user-" + socketThread.getId() + " joined this chat room. " +
                        "Current user count: " + count);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            logger.info("SocketServer has stopped");
            close();
        }
    }

    public void socketDisconnect(ServerSocketThread socket) {
        int current = count.decrementAndGet();
        logger.info("User-" + socket.getId() + " quit chat room, current user count: " + current);
        String roomName = socket.getChatRoom();
        if (roomName == null || "".equals(roomName)) {
            return;
        }

        try {
            removeSocketFromRoom(socket, roomName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前聊天室以及成员信息，并没有使用同步
     */
    public String getRoomList() {
        List<Room> data = new ArrayList<Room>();
        for (Map.Entry<String, List<ServerSocketThread>> entry: rooms.entrySet()) {
            Room room = new Room(entry.getKey(), entry.getValue().size());
            data.add(room);
        }

        return gson.toJson(data);
    }

    /**
     * 将用户添加到某个聊天室
     */
    public synchronized void addSocketToRoom(ServerSocketThread socket, String roomName) throws Exception {
        List<ServerSocketThread> sockets = rooms.get(roomName);
        if (sockets == null) {
            throw new Exception(ErrorMsg.ROOM_NOT_EXIST);
        }

        sockets.add(socket);
        rooms.put(roomName, sockets);
        logger.info("user-" + socket.getId() + " join chat room: " + roomName);
    }

    /**
     * 创建聊天室
     */
    public synchronized void createChatRoom(ServerSocketThread socket, String roomName) throws Exception {
        List<ServerSocketThread> sockets = rooms.get(roomName);
        if (sockets != null) {
            throw new Exception(ErrorMsg.ROOM_EXIST);
        }

        sockets = new ArrayList<ServerSocketThread>();
        sockets.add(socket);
        rooms.put(roomName, sockets);
        logger.info("user-" + socket.getId() + " create chat room: " + roomName);
    }

    /**
     * 从房间中移除某个用户，如果没有用户了，删除房间，同步方法，
     * 不使用concurrentHashMap的原因就是这是个组合操作，存在竞态条件
     */
    public synchronized void removeSocketFromRoom(ServerSocketThread socket, String roomName) throws Exception{
        List<ServerSocketThread> sockets = rooms.get(roomName);
        if (sockets == null) {
            throw new Exception(ErrorMsg.ROOM_NOT_EXIST);
        }

        boolean remove = sockets.remove(socket);
        if (!remove) {
            throw new Exception(ErrorMsg.NOT_IN_THIS_ROOM);
        }

        logger.info("user-" + socket.getId() + " quit chat room: " + roomName);
        if (sockets.size() == 0) {
            rooms.remove(roomName);
            return;
        }

        rooms.put(roomName, sockets);
    }

    /**
     * 分发聊天信息
     */
    public void deliverChatMsg(String roomName, String msg) throws Exception {
        List<ServerSocketThread> sockets = rooms.get(roomName);
        if (sockets == null) {
            throw new Exception(ErrorMsg.ROOM_NOT_EXIST);
        }

        for (ServerSocketThread socket: sockets) {
            socket.sendChatMsg(msg);
        }
    }

    private void close() {
        try {
            alive = false;
            if (server != null && !server.isClosed()) {
                server.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initFromProperties() {
        Properties properties = new Properties();
        try {
            properties.load(this.getClass().getResourceAsStream("/server.properties"));
            this.port = Integer.valueOf(properties.getProperty("port"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                logger.info("Shutdown hook");
                try {
                    // todo close all socket

                    if (!server.isClosed()) {
                        server.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
