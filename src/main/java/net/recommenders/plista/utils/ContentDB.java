package net.recommenders.plista.utils;

import net.recommenders.plista.client.ChallengeMessage;
import net.recommenders.plista.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: alan
 * Date: 2013-08-30
 * Time: 14:28
 * To change this template use File | Settings | File Templates.
 */
public class ContentDB {

    private final static Logger logger = LoggerFactory.getLogger(ContentDB.class);
    private static Connection con;
    String dbFileName = "content.db";
    private HashSet<Long> items;



    public void init() {
        try{
            Class.forName("org.sqlite.JDBC");

            File dbFile = new File(dbFileName);
            boolean dbExists = dbFile.exists();
            con = DriverManager.getConnection("jdbc:sqlite:"+dbFileName);
            if(dbExists)
                con.prepareStatement("ATTACH DATABASE '" + dbFileName + "' AS 'content'").execute();

            Statement stat = con.createStatement();
            stat.executeUpdate("CREATE TABLE IF NOT EXISTS messages("
                    + "id INTEGER,"
                    + "domain NUMERIC,"
                    + "title TEXT,"
                    + "text TEXT,"
                    + "content TEXT,"
                    + "PRIMARY KEY (id));"
                    + "CREATE INDEX IF NOT EXISTS 'domain' ON 'messages' ('domain' ASC);");
            stat.close();
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        }
        catch(SQLException e){
            e.printStackTrace();
        }
    }

    public ContentDB(){
        items = new HashSet<Long>();
    }

    public boolean addMessage(Message message, String content){
        boolean result = false;
        Long itemID = message.getItemID();
        Long domainID = message.getDomainID();
        String title = message.getItemTitle();
        String text = message.getItemText();
        if(items.contains(itemID))
            return true;
        else
            items.add(itemID);
        try{
            Statement stat = con.createStatement();
            PreparedStatement prep = con
                    .prepareStatement("INSERT INTO messages VALUES(?,?,?,?,?);");
            prep.setString(1, itemID.toString());
            prep.setString(2, domainID.toString());
            prep.setString(3, title);
            prep.setString(4, text);
            prep.setString(5, content);
            prep.execute();
            prep.close();
            result = true;
            stat.close();
        }catch(SQLException e){
            logger.error(e.getMessage());
        }
        return result;
    }


    public String getContent(Long itemID, Long domainID){
        String result = null;
        try{
            Statement stat = con.createStatement();
            ResultSet res = stat.executeQuery("SELECT * FROM messages where domain = '"+domainID
                    +"' and id = '"+ itemID+"'");
            while (res.next())
                result = res.getString("content");
            stat.close();
        }catch (SQLException e){
            logger.error(e.getMessage());
        }
        return result;
    }

    public Message getMessage(Long itemID, Long domainID){
        ChallengeMessage message = null;
        try{
            Statement stat = con.createStatement();
            ResultSet res = stat.executeQuery("SELECT * FROM messages where domain = '"+domainID
                    +"' and id = '"+ itemID+"'");
            while (res.next()){
                message = new ChallengeMessage();
                message.setItemID(itemID);
                message.setDomainID(domainID);
                message.setItemText(res.getString("text"));
                message.setItemTitle(res.getString("title"));
            }
            stat.close();
        }catch (SQLException e){
            logger.error(e.getMessage());
        }
        return message;
    }

    public void disconnect() throws  SQLException{
        con.close();
    }

    public boolean itemExists(Long itemID){
        return items.contains(itemID);
    }

}