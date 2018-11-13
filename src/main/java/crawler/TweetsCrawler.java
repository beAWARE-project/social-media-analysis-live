/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import classification.Classification;
import classification.ImageResponse;
import classification.TextResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import json.Body;
import json.Header;
import json.Message;
import json.Position;
import mykafka.Bus;

/**
 *
 * @author andreadisst
 */
public class TweetsCrawler {
    
    private static Map<String, List<String>> keywordsPerCollection = new HashMap<>();
    private static List<String> useCases = new ArrayList<>();
    private static Bus bus = new Bus();
    private static Gson gson = new Gson();
    private static Client hosebirdClient;
    private static BlockingQueue<String> msgQueue;
    
    public static void main(String[] args) throws InterruptedException, UnknownHostException, UnsupportedEncodingException {
        
        useCases.add("EnglishHeatwave");
        useCases.add("GreekHeatwave");
        
        List<String> keywords = new ArrayList<>();
        keywords.add("heatwave");
        keywordsPerCollection.put("EnglishHeatwave", keywords);
        
        keywords = new ArrayList<>();
        keywords.add("καύσωνας");keywords.add("Κελσίου");keywords.add("θερμοκρασία_ρεκόρ");keywords.add("GSCP_GR");
        keywordsPerCollection.put("GreekHeatwave", keywords);
        
        prepareStreamingAPI();
        
        while (!hosebirdClient.isDone()) {
            System.out.println("new tweet received");
            String msg = msgQueue.take();
            findUseCaseAndInsert(msg);
        }

        bus.close();
        
    }
    
    private static void prepareStreamingAPI() throws UnknownHostException{
        
        List<String> keywords = new ArrayList<>();
        keywords.add("beawaretest");
        
        String TWITTER_API_CONSUMER_KEY = System.getenv("TWITTER_API_CONSUMER_KEY");
        String TWITTER_API_CONSUMER_SECRET = System.getenv("TWITTER_API_CONSUMER_SECRET");
        String TWITTER_API_TOKEN = System.getenv("TWITTER_API_TOKEN");
        String TWITTER_API_SECRET = System.getenv("TWITTER_API_SECRET");
        
        msgQueue = new LinkedBlockingQueue<>(16);
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        hosebirdEndpoint.trackTerms(keywords);
        Authentication hosebirdAuth = new OAuth1(TWITTER_API_CONSUMER_KEY, TWITTER_API_CONSUMER_SECRET, TWITTER_API_TOKEN, TWITTER_API_SECRET);
        ClientBuilder builder = new ClientBuilder()
            .name("Hosebird-Client-01")
            .hosts(hosebirdHosts)
            .authentication(hosebirdAuth)
            .endpoint(hosebirdEndpoint)
            .processor(new StringDelimitedProcessor(msgQueue));
        hosebirdClient = builder.build();
        hosebirdClient.connect();
        
    }
    
    private static void findUseCaseAndInsert(String msg) throws UnknownHostException{
        
        JsonObject obj = new JsonParser().parse(msg).getAsJsonObject();
        if(obj.get("text") != null){
            String text = obj.get("text").getAsString();
            if(obj.has("extended_tweet")){
                JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
                text = extended_tweet.get("full_text").getAsString();
            }
            for(String useCase : useCases){
                List<String> keywords = keywordsPerCollection.get(useCase);
                for(String keyword : keywords){
                    if(text.contains(keyword)){
                        try{
                            System.out.println("Insert tweet to " + useCase);
                            insert(msg, useCase);
                        }catch(Exception e){
                            System.out.println("Error: " + e);
                        }
                        break;
                    }
                }
            }
        }
    }
    
    private static void insert(String msg, String useCase) throws UnknownHostException, NoSuchAlgorithmException, KeyManagementException{
        
        String relevancy = "";
        
        JsonObject obj = new JsonParser().parse(msg).getAsJsonObject();
        
        if(obj.has("retweeted_status")){
            obj.addProperty("is_retweeted_status",true);
        }else{
            obj.addProperty("is_retweeted_status",false);
        }
        
        String text;
        Position position;
        
        text = obj.get("text").getAsString();
        position = getLocation(text); //this could be added to json
        text = cleanText(text);
        text = replaceLocation(text);
        obj.addProperty("text", text);
        
        if(obj.has("extended_tweet")){
            JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
            text = extended_tweet.get("full_text").getAsString();
            position = getLocation(text); //this could be added to json
            text = cleanText(text);
            text = replaceLocation(text);
            obj.getAsJsonObject("extended_tweet").addProperty("full_text", text);
        }
        
        String name = obj.getAsJsonObject("user").get("name").getAsString();
        obj.getAsJsonObject("user").addProperty("name", Cryptonite.getEncrypted(name));
        String screen_name = obj.getAsJsonObject("user").get("screen_name").getAsString();
        obj.getAsJsonObject("user").addProperty("screen_name", Cryptonite.getEncrypted(screen_name));

        if(obj.has("extended_tweet")){
            JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
            if(extended_tweet.has("entities")){
                JsonObject entities = extended_tweet.get("entities").getAsJsonObject();
                if(entities.has("media")){
                    JsonArray media = entities.get("media").getAsJsonArray();
                    if(media.size() > 0){
                        JsonObject image = media.get(0).getAsJsonObject();
                        if(image.has("media_url")){
                            System.out.print("-> image classification ");
                            String imageURL = image.get("media_url").getAsString();
                            ImageResponse ir = Classification.classifyImage(imageURL, useCase);
                            relevancy = String.valueOf(ir.getRelevancy());
                            System.out.print("-> "+relevancy+" ");

                            image.addProperty("dcnn_feature", ir.getDcnnFeature());
                            media.set(0,image);
                            entities.add("media", media);
                            extended_tweet.add("entities", entities);
                            obj.add("extended_tweet", extended_tweet);
                        }
                    }
                }
            }
        }
        else if(obj.has("entities")){
            JsonObject entities = obj.get("entities").getAsJsonObject();
            if(entities.has("media")){
                JsonArray media = entities.get("media").getAsJsonArray();
                if(media.size() > 0){
                    JsonObject image = media.get(0).getAsJsonObject();
                    if(image.has("media_url")){
                        System.out.print("-> image classification ");
                        String imageURL = image.get("media_url").getAsString();
                        ImageResponse ir = Classification.classifyImage(imageURL, useCase);
                        relevancy = String.valueOf(ir.getRelevancy());
                        System.out.print("-> "+relevancy+" ");
                        
                        image.addProperty("dcnn_feature", ir.getDcnnFeature());
                        media.set(0,image);
                        entities.add("media", media);
                        obj.add("entities", entities);
                    }
                }
            }
        }
        
        if(relevancy.equals("")||relevancy.equals("false")){
            if(useCase.equals("ItalianFloods")||useCase.equals("GreekHeatwave")||useCase.equals("SpanishFires")){
                System.out.print("-> text classification ");
                relevancy = Classification.classifyText(text, useCase);
                if(!relevancy.equals("")){
                    System.out.print("-> "+relevancy);
                }
                
            }
        }
        
        System.out.println("");
        if(relevancy.equals("true")){
            obj.addProperty("estimated_relevancy", true);
        }else if(relevancy.equals("false")){
            obj.addProperty("estimated_relevancy", false);
        }
        
        MongoClient mongoClient = MongoAPI.connect();
        DB db = mongoClient.getDB("BeAware");
        DBCollection collection = db.getCollection("Consumer");
        DBCollection collection_backup = db.getCollection("LiveBackup");
        BasicDBObject res = (BasicDBObject) JSON.parse(obj.toString());
        
        collection.insert(res);
        collection_backup.insert(res);
        
        // skip relevancy for now
        //if(relevancy){
            String id = obj.get("id_str").getAsString();

            String language = "";
            if(useCase.contains("English")){
                language = "en-US";
            }else if(useCase.contains("Italian")){
                language = "it-IT";
            }else if(useCase.contains("Greek")){
                language = "el-GR";
            }else if(useCase.contains("Spanish")){
                language = "es-ES";
            }

            long now = System.currentTimeMillis();
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new java.util.Date(now));

            Header header = new Header(Configuration.socialMediaText001, 0, 1, "SMA", "sma-msg-"+now, date, "Actual", "Alert", "citizen", "Restricted", "", "", 0, "", "");

            Body body;
            if(position.getLatitude()==0.0 && position.getLongitude()==0.0){
                body = new Body("SMA", "INC_SMA_"/*+useCase+"_"*/+id, language, date, text);
            }else{
                body = new Body("SMA", "INC_SMA_"/*+useCase+"_"*/+id, language, date, text, position);
            }

            Message message = new Message(header, body);

            String message_str = gson.toJson(message);

            try{
                bus.post(Configuration.socialMediaText001, message_str);
            }catch(IOException | InterruptedException | ExecutionException | TimeoutException e){
                System.out.println("Error on send: " + e);
            }
            
        //}
    }
    
    private static Position getLocation(String msg){
        Position position = new Position(0,0); //default Thessaloniki?
        
        if(msg.contains("ΚΘ_4")){
            return new Position(40.6207, 22.9649);
        }else if(msg.contains("ΚΘ_6")){
            return new Position(40.6019, 22.9736);
        }else if(msg.contains("ΠΑΤ")){
            return new Position(40.6325, 22.9407);
        }else if(msg.contains("ΠΧ")){
            return new Position(40.6008, 22.9701);
        }else if(msg.contains("ΠΤ")){
            return new Position(40.6140, 22.9722);
        }else if(msg.contains("ΔΕ")){
            return new Position(40.6333, 22.9495);
        }else if(msg.contains("ΔΤ")){
            return new Position(40.6266, 22.9526);
        }else if(msg.contains("ΔΒ")){
            return new Position(40.5956, 22.9600);
        }
        
        return position;
    }
    
    private static String replaceLocation(String msg){
        String tweet = msg;
        
        tweet = tweet.replace("ΚΘ_4", "4ο ΚΑΠΗ").replace("ΚΘ_6", "6ο ΚΑΠΗ").replace("ΠΑΤ", "Πλατεία Αριστοτέλους").replace("ΠΧ", "Χαριλάου").replace("ΠΤ", "Τούμπα")
                .replace("ΔΕ", "Εγνατία").replace("ΔΤ", "Τσιμισκή").replace("ΔΒ", "Βούλγαρη");
        
        return tweet;
    }
    
    private static String cleanText(String msg){
        String tweet = msg;
        String pattern = "(?:\\s|\\A)[@]+([A-Za-z0-9-_]+)";
        
        tweet = tweet.replace("#THIS_IS_A_TEST", "").replace("#beawaretest", "");
        
        tweet = tweet.replaceAll(pattern, " @user");
        
        return tweet;
    }
}
