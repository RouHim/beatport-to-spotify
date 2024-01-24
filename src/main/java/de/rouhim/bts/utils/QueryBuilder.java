package de.rouhim.bts.utils;

import de.rouhim.bts.domain.BeatportTrack;

import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {
    private static final List<String> keyWordsToRemove = mutateKeyWords("&amp;", "+", "=", "'", "&", "feat", "ft", "featuring", "vs", "versus");

    public static void main(String[] args) {
        System.out.println(optimizeQuery("Love Is Gone (feat. Dylan Matthew)"));
        System.out.println(optimizeQuery("Not An Addict feat. K's Choice (Original mix)"));
        System.out.println(optimizeQuery("Hava feat. Dr Phunk"));
    }

    public static String build(BeatportTrack beatportTrack) {
        String primaryTitle = beatportTrack.title();
        String firstArtist = beatportTrack.artists().getFirst();
        return optimizeQuery(String.format("%s %s", primaryTitle, firstArtist));
    }

    private static String optimizeQuery(String query) {
        return removeKeyWords(removeBrackets(query));
    }

    public static String removeBetween(String text, String start, String end) {
        int first = text.indexOf(start);
        int last = text.indexOf(end) + 1;
        int diff = last - first;

        if (diff > 0) {
            String seq = text.substring(first, last);
            text = text.replace(seq, "");
        }

        return text;
    }

    public static String removeKeyWords(String query) {
        for (String s : keyWordsToRemove) {
            query = query.replace(" " + s + " ", " ");
        }
        query = query.replace(", ", " ");

        return query;
    }

    public static String removeBrackets(String query) {
        String normal1 = "(";
        String normal2 = ")";
        String edge1 = "[";
        String edge2 = "]";
        String swift1 = "{";
        String swift2 = "}";

        if (query.contains(normal1) && query.contains(normal2)) {
            query = removeBetween(query, normal1, normal2);
        }

        if (query.contains(edge1) && query.contains(edge2)) {
            query = removeBetween(query, edge1, edge2);
        }

        if (query.contains(swift1) && query.contains(swift2)) {
            query = removeBetween(query, swift1, swift2);
        }

        return query.trim().replace("  ", " ");
    }

    public static List<String> mutateKeyWords(String... keywords) {
        List<String> keyWords = new ArrayList<>();

        for (String keyWord : keywords) {
            keyWords.addAll(mutate(keyWord));
        }

        return keyWords;
    }

    private static List<String> mutate(String s) {
        s = s.toLowerCase();
        ArrayList<String> mut = new ArrayList<>();
        mut.add(s);
        mut.add(s + ".");
        mut.add(s.toUpperCase());
        mut.add(s.toUpperCase() + ".");
        mut.add(firstCharToUppercase(s));
        mut.add(firstCharToUppercase(s) + ".");
        return mut;
    }

    public static String firstCharToUppercase(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}