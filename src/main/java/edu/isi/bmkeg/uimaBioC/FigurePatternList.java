package edu.isi.bmkeg.uimaBioC;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FigurePatternList {
    
    private static FigurePatternList instance;
	List<Pattern> figPatt = new ArrayList<Pattern>();
	List<Pattern> figsPatt = new ArrayList<Pattern>();
 
    static {
    	
        instance = new FigurePatternList();
        
		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		// Include 'S' to denote supplemental figures 
		// included as well
		//
		String b = "\\s*[Ff]ig(ure|.){0,1}";
		String e = "\\s*(\\)|\\w{2,}|\\p{Punct})";
		//
		// 0. No alphanumeric codes at all
		instance.figPatt.add(Pattern.compile(b + "\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));
		//
		// 1. Delineated by brackets
		instance.figPatt.add(Pattern.compile("\\(" + b + "\\s*(S{0,1}\\d+.*?)\\)"));
		
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		instance.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\p{Punct}"));

		//
		// 3. Single Alphanumeric codes, followed by words.
		instance.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\s+\\w{2,}"));

		//
		// 4. Fig 8, a and b).
		instance.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])" + e));

		//
		// 5. Fig. 3, a-c).
		instance.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d[\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		//
		// 6. Fig. c).
		instance.figPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c).
		instance.figsPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
        
    }
     
    private FigurePatternList(){
        System.out.println("Creating FigurePatternList object...");
    }
     
    public static FigurePatternList getInstance(){
        return instance;
    }
     
    public void testSingleton(){
        System.out.println("Hey.... Instance got created...");
    }

}
