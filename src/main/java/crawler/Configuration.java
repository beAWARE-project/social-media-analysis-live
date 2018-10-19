/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import com.google.common.collect.Lists;
import java.util.List;

/**
 *
 * @author andreadisst
 */
public class Configuration {

    //public static List<String> languages = Lists.newArrayList("English", "Greek", "Italian", "Spanish", "Danish");
    //public static List<String> pilots = Lists.newArrayList("Fires", "Floods", "Heatwave");

    public static String JAAS_CONFIG_PROPERTY = "java.security.auth.login.config";
    public static String key = "key";
    public static String kafka_admin_url = "https://kafka-admin-prod02.messagehub.services.eu-gb.bluemix.net:443";

    public static String socialMediaText001 = "TOP001_SOCIAL_MEDIA_TEXT";
}
