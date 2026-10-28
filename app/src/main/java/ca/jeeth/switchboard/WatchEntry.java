package ca.jeeth.switchboard;

import java.util.*;

/** Published metadata only. No screenshots, service accounts or inferred viewing progress. */
final class WatchEntry {
    final String key,pkg,title,episode,seasonNumber,episodeNumber,intentUri,artUri;
    final long engagement,position,duration;
    final int type;
    final boolean browsable;
    WatchEntry(String key,String pkg,String title,String episode,String seasonNumber,String episodeNumber,String intentUri,long engagement,long position,long duration,int type,boolean browsable){
        this(key,pkg,title,episode,seasonNumber,episodeNumber,intentUri,engagement,position,duration,type,browsable,"");
    }
    WatchEntry(String key,String pkg,String title,String episode,String seasonNumber,String episodeNumber,String intentUri,long engagement,long position,long duration,int type,boolean browsable,String artUri){
        this.artUri=artUri;
        this.key=pkg+":"+key;this.pkg=pkg;this.title=title;this.episode=episode;this.seasonNumber=seasonNumber;this.episodeNumber=episodeNumber;this.intentUri=intentUri;
        this.engagement=Math.max(0,engagement);this.position=Math.max(0,position);this.duration=Math.max(0,duration);this.type=type;this.browsable=browsable;
    }
    String episodeLabel(){
        String number=(!seasonNumber.isEmpty()?"S"+seasonNumber+" ":"")+(!episodeNumber.isEmpty()?"E"+episodeNumber:"");
        return number.trim().isEmpty()?episode:number.trim()+(!episode.isEmpty()?" · "+episode:"");
    }
    int progress(){return duration<=0?0:(int)Math.min(1000,1000.0*position/duration);}
    boolean useful(){return browsable&&type>=0&&type<=2&&!title.trim().isEmpty()&&!intentUri.trim().isEmpty()&&!(type==0&&duration>0&&position>=duration);}
    static List<WatchEntry> ordered(List<WatchEntry> source,Map<String,Long> hidden){
        List<WatchEntry> sorted=new ArrayList<>();
        for(WatchEntry e:source)if(e.useful()&&(!hidden.containsKey(e.key)||e.engagement>hidden.get(e.key)))sorted.add(e);
        sorted.sort(Comparator.comparingLong((WatchEntry e)->e.engagement).reversed().thenComparing(e->e.title).thenComparing(e->e.key));
        Set<String> seen=new HashSet<>();sorted.removeIf(e->!seen.add(e.key));return sorted;
    }
}
