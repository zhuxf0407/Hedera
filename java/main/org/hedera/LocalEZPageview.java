package org.hedera;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLDecoder;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import tl.lin.data.array.ArrayListOfIntsWritable;
import tuan.io.FileUtility;

/** Handle the pagecounts-ez dumps in local mode */
public class LocalEZPageview {

	private static final DateTimeFormatter dtfMonth = DateTimeFormat
			.forPattern("YYYY-mm");
	private static final DateTimeFormatter dtfMonthPrinter = DateTimeFormat
			.forPattern("YYYYmm");

	public static void main(String[] args) throws IOException {


		DateTime month = dtfMonth.parseDateTime(args[2]);
		int dayOfMonth = month.dayOfMonth(	).getMaximumValue();

		ArrayListOfIntsWritable value = new ArrayListOfIntsWritable((33) * 3 / 2 + 2);
		value.setSize(dayOfMonth + 2);

		int monthAsInt = Integer.parseInt(dtfMonthPrinter.print(month));

		value.set(0, monthAsInt);

		InputStream is = null;

		FileInputStream fis = new FileInputStream(args[0]);
		BufferedInputStream bis = new BufferedInputStream(fis);


		if (args[0].endsWith(".bz2")) {
			is = new BZip2CompressorInputStream(bis);
		}
		else {
			is = bis;
		}

		long begin = System.currentTimeMillis();

		FileWriter writer = new FileWriter(args[1]);
		Writer o = new BufferedWriter(writer);

		for (String line : FileUtility.readLines(is,null)) {
			/*if (++lineCnt % 1000000 == 0) {
				System.out.println(System.currentTimeMillis() + ": processed " + lineCnt);
			}*/
			if (line.length() < 4) continue;
			if ((line.charAt(0) != 'e' && line.charAt(0) != 'E') 
					|| (line.charAt(1) != 'n' && line.charAt(0) != 'E') 
					|| line.charAt(2) != '.' 
					|| (line.charAt(3) != 'z' && line.charAt(0) != 'Z')) {
				continue;
			}
			int i = line.indexOf(' ');
			int j = line.indexOf(' ', i+1);

			/*
			 * =================================================================
			 * Process the title
			 * =================================================================
			 */
			String title = line.substring(i+1, j);
			try {
				title = URLDecoder.decode(title, "UTF-8");
			} catch (Exception e) {
				// e.printStackTrace();
				continue;
			}
			if (title.length() > 50) continue;

			// heuristics:
			if (title.startsWith("Category:") || title.startsWith("File:")
					|| title.startsWith("Wikipedia:")
					|| title.startsWith("Wikipedia/")
					|| title.startsWith("Wikipedia#")
					|| title.startsWith("User:")
					|| title.startsWith("Special:")
					|| title.startsWith("Portal:")
					|| title.startsWith("Portal_talk:")
					|| title.startsWith("Talk:") || title.contains("/Talk:")
					|| title.startsWith("Help:")
					|| title.startsWith("Template:")
					|| title.startsWith("/Template:")					
					|| title.startsWith("/Template_talk:")
					|| title.startsWith("Translate:")
					|| title.startsWith("/Wikipedia_talk:")
					|| title.startsWith("http://")
					|| title.startsWith("https://")
					|| title.startsWith("//upload")
					|| title.startsWith("/File:") || title.endsWith(".html")
					|| title.endsWith("HTML") || title.endsWith(".jpg")
					|| title.endsWith(".txt") || title.endsWith(".TXT")
					|| title.endsWith(".JPG") || title.endsWith(".gif")
					|| title.endsWith(".GIF") || title.endsWith(".css")
					|| title.endsWith(".CSS") || title.endsWith(".bmp")
					|| title.endsWith(".php") || title.endsWith(".BMP")
					|| title.endsWith(".svg") || title.endsWith(".SVG")
					|| title.endsWith(".OGG") || title.endsWith(".ogg")
					|| title.endsWith(".ogv") || title.endsWith(".webm")
					||

					// different language & projects
					title.startsWith("hr:") || title.startsWith("hu:")
					|| title.startsWith("simple:")) {
				continue;
			}
			int tmpIdx = 0;

			// heuristics: Normalize titles based on:
			// - Cut off the trailing anchor (following the #), or query string
			// (following the &)
			// - Cut off the leading and trailing quotes (double or triple)
			// - Cut off the leading and trailing "_"
			// Capitalize the first character
			if ((tmpIdx = title.indexOf('#')) > 0) {
				title = title.substring(0, tmpIdx);
			}
			if ((tmpIdx = title.indexOf('&')) > 0) {
				title = title.substring(0, tmpIdx);
			}
			if (title.startsWith("#") || title.startsWith("_")) {
				title = title.substring(1, title.length());
			}
			if (title.endsWith("_")) {
				title = title.substring(0, title.length()-1);
			}
			if (title.startsWith("'''") && title.endsWith("'''")) {
				if (title.length() > 6) {
					title = title.substring(3, title.length() - 3);
				}
				else continue;
			} else if (title.startsWith("''") && title.endsWith("''")) {
				if (title.length() > 4) {
					title = title.substring(2, title.length() - 2);
				}
				else continue;
			} else if (title.startsWith("\"") && title.endsWith("\"")) {
				if (title.length() > 2) {
					title = title.substring(1, title.length() - 1);
				}
				else continue;
			} else if (title.startsWith("wiki/")) {
				title = title.substring(5, title.length());
			}
			
			title = title.trim();

			if (title.isEmpty()) {
				continue;
			}
			char chr = title.charAt(0);
			if (chr >= 'a' && chr <= 'z') {
				char CHR = (char) (chr - 32);
				title = CHR + title.substring(1,title.length());
			}

			title = title.replaceAll("\\s+", "_");
			o.write(title);

			/*
			 * =================================================================
			 * Process the time series
			 * =================================================================
			 */
			int k = line.indexOf(' ', j+1);
			int total = Integer.parseInt(line.substring(j+1, k));

			String compactTs = line.substring(k+1);

			// reset the time series 
			resetTimeseries(value);
			value.set(1, total);

			// decode the time series
			int idx = 0;

			while (idx >= 0) {
				int nextIdx = compactTs.indexOf(',',idx+1);
				if (nextIdx < 0) {
					break;
				}

				// everything from idx+1 to nextIdx is for one day
				extractViewsForOneDay(compactTs, idx+1, nextIdx, value);

				idx = nextIdx;
			}

			StringBuilder sb = new StringBuilder();
			for (int m = 0; m < value.size(); m++) {
				sb.append(' ');
				sb.append(value.get(m));				
			}

			// System.out.println(sb.toString());
			o.write(sb.toString());
			o.write("\n");
		}

		System.out.println("Finished in " + (System.currentTimeMillis() - begin) / 1000 + " seconds.");

		o.close();
		is.close();		
		fis.close();
	}

	/** return the zero-based index of the day */
	private static int decodeDay(char dayChr) {
		if (dayChr >= 'A' && dayChr <= 'Z') {
			return (dayChr - 'A');
		}
		else if (dayChr == '[') {
			return 26;
		}
		else if (dayChr == '\\') {
			return 27;
		}
		else if (dayChr == ']') {
			return 28;
		}
		else if (dayChr == '^') {
			return 29;
		}
		else if (dayChr == '_') {
			return 30;
		}
		else {
			throw new IllegalArgumentException(
					"Unknown day: " + dayChr);
		}
	}

	/** return the zero-based index of the hour in a day */
	private static int decodeHour(char chr) {
		if (chr >= 'A' && chr <= 'Z') {
			return (chr - 'A');
		}
		else {
			throw new IllegalArgumentException(
					"Unknown hour: " + chr);
		}
	}

	private static void resetTimeseries(ArrayListOfIntsWritable value) {
		for (int i = 1; i < value.size(); i++) {
			value.set(i, 0);
		}
	}

	private static void extractViewsForOneDay(CharSequence compactTs, 
			int begin, int end, ArrayListOfIntsWritable value) {

		// first character is the day index
		int dayIdx = decodeDay(compactTs.charAt(begin));	

		// heuristic, maximum number of views per hour is 999
		int hourIdx = -1;
		int hourView = 0;
		int dayView = 0;

		for (int i = begin + 1; i < end; i++) {
			char chr = compactTs.charAt(i);
			if (chr >= '0' && chr <= '9') {
				hourView = hourView * 10 + (chr - '0');
			}
			
			// some hour count is missing
			else if (chr == '?') {
				// Do nothing, just move on
			}
			
			else {
				if (hourIdx >= 0) {

					// TODO: separate the hour processing option here
					dayView += hourView;
				}
				hourIdx = decodeHour(chr);
				hourView = 0;					
			}
		}

		// last hour slot
		if (hourIdx >= 0 && hourView > 0) {
			dayView += hourView;
		}

		value.set(dayIdx + 2, dayView);
	}
}
