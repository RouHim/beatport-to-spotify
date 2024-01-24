package de.rouhim.bts.utils;

import java.util.Scanner;

public class Utils {

    public static String readInput(String enterMessage) {
        Scanner scanner = new Scanner(System.in);
        System.out.println(enterMessage);
        return scanner.next();
    }
}
