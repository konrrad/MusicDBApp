package edu.agh.services;

import edu.agh.entities.Artist;
import edu.agh.entities.Category;
import edu.agh.entities.Listener;
import edu.agh.entities.Song;

import java.util.*;

public class ListenerService extends GenericService<Listener> {

    private static final int SONGS_TO_FETCH = 10;
    private static final SongService songService=new SongService();

    @Override
    Class<Listener> getEntityType() {
        return Listener.class;
    }

    public void addNewListener(final String name) {
        if(findListenerByName(name) != null){
            System.out.println("This listener is already in database!");
            return;
        }

        createOrUpdate(new Listener(name));
        System.out.println(String.format("Added new listener named \"%s\"",name));
    }

    public Collection<Song> getRecommendationsByCategory(final String name) {
        final Listener listener = findListenerByName(name);
        if(listener == null){
            System.out.println("Listener doesn't exist in database!");
            return Collections.emptyList();
        }

        Category category=listener.getRandomLikedCategory();
        if(category==null){
            category = listener.getRandomViewedCategory();
        }
        if(category==null){
            System.out.println("Database don't know what category listener likes!");
            return Collections.emptyList();
        }

        System.out.println(String.format("Getting recommendation for \"%s\" from \"%s\"",name,Category.toString(category)));
        return getSongsForListenerFromCategory(category,name);
    }

    private Collection<Song> getSongsForListenerFromCategory(Category category,String listenerName) {
        final Map<String, Object> params = new HashMap<>();
        params.put("category", Category.toString(category));
        params.put("listener_name", listenerName);
        params.put("num_of_songs", SONGS_TO_FETCH);
        final String query = "MATCH (all_from_cat:Song{category: $category}) WHERE NOT (:Listener{name: $listener_name})--(all_from_cat) RETURN DISTINCT id(all_from_cat) LIMIT $num_of_songs";
        Collection<Long> songsIds = getIdsFromMap(executor.query(query, params));

        return songService.getEntitiesById(songsIds);
    }


    public Collection<Song> getRecommendationsBySimilarListeners(final String listenerName) {
        final Listener listener = findListenerByName(listenerName);
        if(listener == null){
            System.out.println("Listener doesn't exist in database!");
            return Collections.emptyList();
        }

        System.out.println(String.format("Getting recommendation for \"%s\" by similar listeners.",listenerName));
        return getSongsForListenerFromSimilarListeners(listenerName);
    }

    private Collection<Song> getSongsForListenerFromSimilarListeners(final String listenerName)
    {
        final Map<String, Object> params = new HashMap<>();
        params.put("listener_name", listenerName);
        params.put("num_of_songs", SONGS_TO_FETCH);
        final String query = "MATCH (list:Listener {name: $listener_name})-->(song:Song)<--(anonther_list:Listener) " +
                "WHERE (list<>anonther_list) AND (anonther_list<>list) MATCH (anonther_list)-->(anonther_list_song:Song) " +
                "WHERE (anonther_list_song<>song) RETURN DISTINCT id(anonther_list_song) LIMIT $num_of_songs";
        Collection<Long> songsIds=getIdsFromMap(executor.query(query,params));

        return songService.getEntitiesById(songsIds);
    }

    public Collection<Song> getRecommendationsByArtist(final String name) {
        final Listener listener = findListenerByName(name);
        if(listener == null){
            System.out.println("Listener doesn't exist in database!");
            return Collections.emptyList();
        }

        Artist likedArtist = listener.getRandomLikedArtist();
        if(likedArtist==null){
            likedArtist=listener.getRandomViewedArtist();
        }
        if(likedArtist==null){
            System.out.println("Database don't know what artist listener likes!");
            return Collections.emptyList();
        }

        System.out.println(String.format("Getting recommendation for \"%s\" by artist named \"%s\"",name,likedArtist.getName()));
        return getSongsForListenerFromArtist(name,likedArtist);
    }

    private Collection<Song> getSongsForListenerFromArtist(final String listenerName, final Artist artist)
    {
        final HashMap<String,Object> params=new HashMap<>();
        params.put("artist_name",artist.getName());
        params.put("listener_name",listenerName);
        params.put("num_to_fetch",SONGS_TO_FETCH);
        final String query="MATCH (song:Song)--(artist:Artist{name: $artist_name})" +
                "MATCH (listener:Listener{name: $listener_name}) WHERE NOT (song)--(listener)" +
                "RETURN DISTINCT id(song) LIMIT $num_to_fetch";
        Collection<Long> songsIds=getIdsFromMap(executor.query(query,params));

        return songService.getEntitiesById(songsIds);
    }

    public void likeSong(final String listenerName, final String title){
        final Listener listener = findListenerByName(listenerName);
        if(listener == null){
            System.out.println("Listener doesn't exist in database!");
            return;
        }

        final Song song = songService.findSongByName(title);
        if(song == null){
            System.out.println("Song doesn't exist in database!");
            return;
        }

        listener.addLikedSongs(Collections.singletonList(song));
        createOrUpdate(listener);
        System.out.println(String.format("Listener \"%s\" liked \"%s\"",listenerName,title));
    }

    public void viewedSong(final String listenerName, final String title){
        final Listener listener = findListenerByName(listenerName);
        if(listener == null){
            System.out.println("Listener doesn't exist in database!");
            return;
        }

        final Song song = songService.findSongByName(title);
        if(song == null){
            System.out.println("Song doesn't exist in database!");
            return;
        }

        listener.addViewedSongs(Collections.singletonList(song));
        createOrUpdate(listener);
        System.out.println(String.format("Listener \"%s\" viewed \"%s\"",listenerName,title));
    }

    private Listener findListenerByName(final String name){
        final HashMap<String,Object> listenerParams = new HashMap<>();
        listenerParams.put("listener_name",name);
        final String listenerQuery = "MATCH (n:Listener{name:$listener_name}) RETURN id(n) LIMIT 1";
        Iterator<Map<String,Object>> itr=executor.query(listenerQuery,listenerParams);

        if(!itr.hasNext()) return null;

        final Long id=(Long)itr.next().get("id(n)");

        return find(id);
    }
}
