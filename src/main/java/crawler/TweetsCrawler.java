/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import classification.Classification;
import classification.ImageResponse;
import classification.Validation;
import classification.VerificationResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
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
    
    public static void main(String[] args) throws InterruptedException, UnknownHostException, UnsupportedEncodingException, NoSuchAlgorithmException, KeyManagementException {
        
        useCases.add("EnglishFloods");
        useCases.add("ItalianFloods");
        
        List<String> keywords = new ArrayList<>();
        keywords.add("flooding");
        keywordsPerCollection.put("EnglishFloods", keywords);
        
        keywords = new ArrayList<>();
        keywords.add("alluvione");keywords.add("alluvionevicenza");keywords.add("allagamento");keywords.add("bacchiglione");keywords.add("fiumepiena");
        keywords.add("allertameteo");keywords.add("sottopassoallagato");keywords.add("allertameteovicenza");keywords.add("esondazione");keywords.add("livellofiume");
        keywordsPerCollection.put("ItalianFloods", keywords);
        
        /*useCases.add("EnglishHeatwave");
        useCases.add("GreekHeatwave");
        
        List<String> keywords = new ArrayList<>();
        keywords.add("heatwave");
        keywordsPerCollection.put("EnglishHeatwave", keywords);
        
        keywords = new ArrayList<>();
        keywords.add("καύσωνας");keywords.add("Κελσίου");keywords.add("θερμοκρασία_ρεκόρ");keywords.add("GSCP_GR");
        keywordsPerCollection.put("GreekHeatwave", keywords);*/
        
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
    
    private static void findUseCaseAndInsert(String msg) throws UnknownHostException, NoSuchAlgorithmException, KeyManagementException{
        
        JsonObject obj = new JsonParser().parse(msg).getAsJsonObject();
        String text = getText(obj);
        if(!text.equals("")){
            for(String useCase : useCases){
                List<String> keywords = keywordsPerCollection.get(useCase);
                for(String keyword : keywords){
                    if(text.contains(keyword)){
                        try{
                            System.out.print("New tweet to " + useCase + " ");
                            save(msg, useCase);
                        }catch(UnknownHostException e){
                            System.out.println("Error: " + e);
                        }
                        break;
                    }
                }
            }
        }
    }
    
    private static void save(String msg, String useCase) throws UnknownHostException, NoSuchAlgorithmException, KeyManagementException{
        
        JsonObject obj = new JsonParser().parse(msg).getAsJsonObject();
        
        if(obj.has("retweeted_status")){
            obj.addProperty("is_retweeted_status",true);
        }else{
            obj.addProperty("is_retweeted_status",false);
        }
        
        String text = getText(obj);
        Position position = getLocation(text); //this could be added to json
        obj = updateText(obj);
        text = getText(obj);
        
        /* STEP ONE - Detect fake tweets */
        
        boolean isVerified = true;
        
        JsonObject user = obj.get("user").getAsJsonObject();
        String user_id = user.get("id_str").getAsString();
        if(!user_id.equals("920984955047567360")){
            VerificationResponse verification = Validation.verifyTweet(obj.toString());
            isVerified = verification.getPredictedValue();
            System.out.println("-> verification : "+isVerified+" ");
            JsonObject verificationObj = new JsonObject();
            verificationObj.addProperty("predicted", isVerified);
            verificationObj.addProperty("confidence", verification.getConfidenceValue());
            obj.add("verification", verificationObj);
        }
        
        if(!isVerified){
            insert(obj, useCase);
        }else{
            /* STEP TWO - Check emoticons/emojis */

            boolean emoticon_relevancy = Validation.EmoticonsEstimation(text);
            System.out.print("-> emoticon classification : "+emoticon_relevancy+" ");
            if(!emoticon_relevancy){
                obj.addProperty("emoticon_relevancy", false);
                insert(obj, useCase);
            }else{
                obj.addProperty("emoticon_relevancy", true);

                /* STEP THREE - Classificy based on visual or textual information */

                boolean estimated_relevancy = false;
                String imageURL = getImageURL(obj);
                if(!imageURL.equals("")){
                    System.out.print("-> image classification ");
                    ImageResponse ir = Classification.classifyImage(imageURL, useCase);
                    estimated_relevancy = ir.getRelevancy();
                    System.out.print(": "+estimated_relevancy+" ");
                    obj.addProperty("dcnn_feature", ir.getDcnnFeature());
                }

                if(estimated_relevancy){
                    obj.addProperty("estimated_relevancy", true);
                    insert(obj, useCase);
                    forward(obj, useCase, position);
                }else if(!estimated_relevancy || imageURL.equals("")){
                    if(useCase.equals("ItalianFloods")||useCase.equals("GreekHeatwave")||useCase.equals("SpanishFires")){
                        System.out.print("-> text classification ");
                        String estimated_relevancy_str = Classification.classifyText(text, useCase);
                        if(estimated_relevancy_str.equals("")){
                            if(!imageURL.equals("")){ obj.addProperty("estimated_relevancy", false); }
                            insert(obj, useCase);
                        }else if(estimated_relevancy_str.equals("true")){
                            System.out.print(": "+estimated_relevancy_str+" ");
                            obj.addProperty("estimated_relevancy", true);
                            insert(obj, useCase);
                            forward(obj, useCase, position);
                        }else if(estimated_relevancy_str.equals("false")){
                            System.out.print(": "+estimated_relevancy_str+" ");
                            obj.addProperty("estimated_relevancy", false);
                            insert(obj, useCase);
                        }
                    }else{
                        if(!imageURL.equals("")){
                            obj.addProperty("estimated_relevancy", false);
                            insert(obj, useCase);
                        }else{
                            insert(obj, useCase);
                            forward(obj, useCase, position);
                        }
                    }
                }
            }
        }
    }
    
    private static String getText(JsonObject obj){
        String text = "";
        if(obj.has("extended_tweet")){
            JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
            text = extended_tweet.get("full_text").getAsString();
        }else if(obj.get("text") != null){
            text = obj.get("text").getAsString();
        }
        return text;
    }
    
    private static String getImageURL(JsonObject obj){
        String imageURL = "";
        if(obj.has("extended_tweet")){
            JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
            if(extended_tweet.has("entities")){
                JsonObject entities = extended_tweet.get("entities").getAsJsonObject();
                if(entities.has("media")){
                    JsonArray media = entities.get("media").getAsJsonArray();
                    if(media.size() > 0){
                        JsonObject image = media.get(0).getAsJsonObject();
                        if(image.has("media_url")){
                            imageURL = image.get("media_url").getAsString();
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
                        imageURL = image.get("media_url").getAsString();
                    }
                }
            }
        }
        return imageURL;
    }
    
    private static JsonObject updateText(JsonObject obj){
        if(obj.has("extended_tweet")){
            JsonObject extended_tweet = obj.get("extended_tweet").getAsJsonObject();
            String text = extended_tweet.get("full_text").getAsString();
            text = cleanText(text);
            text = replaceLocation(text);
            obj.getAsJsonObject("extended_tweet").addProperty("full_text", text);
        }else if(obj.get("text") != null){
            String text = obj.get("text").getAsString();
            text = cleanText(text);
            text = replaceLocation(text);
            obj.addProperty("text", text);
        }
        return obj;
    }
    
    private static void insert(JsonObject obj, String useCase){
        
        String name = obj.getAsJsonObject("user").get("name").getAsString();
        obj.getAsJsonObject("user").addProperty("name", Cryptonite.getEncrypted(name));
        String screen_name = obj.getAsJsonObject("user").get("screen_name").getAsString();
        obj.getAsJsonObject("user").addProperty("screen_name", Cryptonite.getEncrypted(screen_name));
        
        try {
            MongoClient mongoClient = MongoAPI.connect();
            DB db = mongoClient.getDB("BeAware");
            DBCollection collection = db.getCollection("Consumer");
            DBCollection collection_backup = db.getCollection("LiveBackup");
            BasicDBObject res = (BasicDBObject) JSON.parse(obj.toString());

            collection.insert(res);
            collection_backup.insert(res);
            System.out.print("-> saved\n");
        } catch (UnknownHostException | NoSuchAlgorithmException | KeyManagementException ex) {
            
        }
        
    }
    
    private static void forward(JsonObject obj, String useCase, Position position){
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
            body = new Body("SMA", "INC_SMA_"/*+useCase+"_"*/+id, language, date, getText(obj));
        }else{
            body = new Body("SMA", "INC_SMA_"/*+useCase+"_"*/+id, language, date, getText(obj), position);
        }

        Message message = new Message(header, body);

        String message_str = gson.toJson(message);

        try{
            bus.post(Configuration.socialMediaText001, message_str);
        }catch(IOException | InterruptedException | ExecutionException | TimeoutException e){
            System.out.println("Error on send: " + e);
        }
    }
    
    private static Position getLocation(String msg){
        Position position = new Position(0,0); //default Thessaloniki?
        
        if(msg.contains("S32ap")){
            return new Position(45.5493, 11.5497);
        }else if(msg.contains("M90xz")){
            return new Position(45.5502, 11.5505);
        }else if(msg.contains("3vg87")){
            return new Position(45.5505, 11.5450);
        }else if(msg.contains("F77ad")){
            return new Position(45.5522, 11.5494);
        }else if(msg.contains("C44ud")){
            return new Position(45.5455, 11.5354);
        }
        
        /*if(msg.contains("ΚΘ_4")){
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
        }*/
        
        return position;
    }
    
    private static String replaceLocation(String msg){
        String tweet = msg;
        
        tweet = tweet.replace("S32ap", "Matteotti").replace("M90xz", "Angeli").replace("C44ud", "Vicenza").replace("F77ad", "Bacchiglione").replace("3vg87","Pusterla");
        
        /*tweet = tweet.replace("ΚΘ_4", "4ο ΚΑΠΗ").replace("ΚΘ_6", "6ο ΚΑΠΗ").replace("ΠΑΤ", "Πλατεία Αριστοτέλους").replace("ΠΧ", "Χαριλάου").replace("ΠΤ", "Τούμπα")
                .replace("ΔΕ", "Εγνατία").replace("ΔΤ", "Τσιμισκή").replace("ΔΒ", "Βούλγαρη");*/
        
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
