/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package classification;

/**
 *
 * @author andreadisst
 */
public class Validation {
    
    private static final String[] EMOTICONS = {":‑)",":)",":-]",":]",":-3",":3",":->",":>","8-)","8)",":-}",":}",":o)",":c)",":^)","=]","=)", //smiley or happy face
                                  ":‑D"," :D ","8‑D","8D"," x‑D "," xD "," X‑D "," XD "," =D "," =3 ","B^D", //laughing, big grin, laugh with glasses, or wide-eyed surprise
                                  ":'‑)",":')", //tears of happiness
                                  ":-*",":*",":×", //kiss
                                  ";‑)"," ;)","*-)","*)",";‑]",";]",";^)",":‑,"," ;D", //wink, smirk
                                  ":‑P"," :P"," X‑P "," XP "," x‑p "," xp ",":‑p"," :p ",":‑Þ",":Þ",":‑þ",":þ",":‑b"," :b "," d: "," =p ",">:P",  //tongue sticking out, cheeky/playful, blowing a raspberry
                                  "<3" //heart
                                 };
    
    private static final String[] EMOJIS = {"☺","️","🙂","😊","😀","😁", //smiley or happy face
                               "😃","😄","😆","😍", //laughing, big grin, laugh with glasses, or wide-eyed surprise
                               "😂", //tears of happiness
                               "😗","😙","😚","😘","😍", //kiss
                               "😉","😜","😘", //wink, smirk
                               "😛","😝","😜","🤑", //tongue sticking out, cheeky/playful, blowing a raspberry
                               "❤" //heart
                              };
            
    /*public static void main(String[] args) {
        String x = "";
        System.out.println(EmoticonsEstimation(x));
    }*/
    
    public static boolean EmoticonsEstimation(String text){
        for(String emoticon : EMOTICONS){
            if(text.contains(emoticon)){
                return false;
            }
        }
        for(String emoji : EMOJIS){
            if(text.contains(emoji)){
                return false;
            }
        }
        return true;
    }
    
    /*public static void IsFakeTweet(String text){
        
    }*/
    
}
