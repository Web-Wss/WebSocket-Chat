package com.websocket.ws;

import com.alibaba.fastjson.JSON;
import com.websocket.config.GetHttpSessionConfig;
import com.websocket.utils.MessageUtils;
import com.websocket.ws.pojo.Message;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/chat",configurator = GetHttpSessionConfig.class)
@Component
public class ChatEndpoint {

    private static final Map<String, Session> onlineUsers = new ConcurrentHashMap<>();

    private HttpSession httpSession;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
//        1、将session进行保存
        this.httpSession = (HttpSession) config.getUserProperties().get(HttpSession.class.getName());
        String user = (String) this.httpSession.getAttribute("user");
        onlineUsers.put(user,session);
//        2、广播消息。需要将登录的所有用户推送给所有用户
        String message = MessageUtils.getMessage(true, null, getFriends());
        broadcastAllUsers(message);
    }

    private Object getFriends() {
        return onlineUsers.keySet();
    }

    private void broadcastAllUsers(String message) {
//        遍历map集合
        Set<Map.Entry<String, Session>> entries = onlineUsers.entrySet();
        for (Map.Entry<String, Session> entry : entries) {
            Session session = entry.getValue();
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*
    * 浏览器发送消息到服务端，该方法被调用
    * */
    @OnMessage
    public void onMessage(String message) {
//        将消息推送给指定的用户
        Message msg = JSON.parseObject(message, Message.class);
//        获取发送消息的用户
        String toName = msg.getToName();
        String mess = msg.getMessage();
//        获取接收方用户对象的session对象
        Session session = onlineUsers.get(toName);
        if (session != null) {
            try {
                session.getBasicRemote().sendText(MessageUtils.getMessage(false, (String) httpSession.getAttribute("user"), mess));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*
    * 断开websocket 连接时被调用
    * */
    @OnClose
    public void onClose() {
//        1、从onlineUsers中剔除当前用户的session对象
        onlineUsers.remove(httpSession.getAttribute("user"));
//        2、通知其他所有的用户，当前用户下线了
        broadcastAllUsers(MessageUtils.getMessage(true, null, getFriends()));
    }

}
