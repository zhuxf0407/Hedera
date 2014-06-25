/*
 * ClueWeb Tools: Hadoop tools for manipulating ClueWeb collections
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hedera.mapreduce;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.clueweb.util.AnalyzerFactory;
import org.hedera.io.FullRevision;
import org.hedera.io.Revision;
import org.hedera.io.input.WikiFullRevisionJsonInputFormat;
import org.hedera.io.input.WikiRevisionInputFormat;
import org.hedera.io.input.WikiRevisionPageInputFormat;
import org.hedera.util.MediaWikiProcessor;

import tl.lin.data.pair.PairOfIntLong;
import tl.lin.lucene.AnalyzerUtils;
import tuan.hadoop.conf.JobConfig;
import static org.hedera.io.input.WikiRevisionInputFormat.TIME_FORMAT;

/**
 * Compute the term statistics from Wikipedia Revision. This app
 * re-uses the ComputeTermStatistics from ClueWeb tool, so I pasted
 * here the copyright notes from the project:
 * 
 * 
 * ClueWeb Tools: Hadoop tools for manipulating ClueWeb collections
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * 
 * 2014-0624: In this variant, we treat each revision independently, and 
 * compute the term statistics with revisions as documents
 * @author tuan
 *
 */

public class BasicComputeTermStats extends JobConfig implements Tool {
	private static final Logger LOG = Logger.getLogger(BasicComputeTermStats.class);

	private static enum Records {
		TOTAL, PAGES, ERRORS, SKIPPED
	};

	private static Analyzer ANALYZER;

	private static final String HADOOP_DF_MIN_OPTION = "df.min";
	private static final String HADOOP_DF_MAX_OPTION = "df.max";

	// All begin and end time are in ISOTimeFormat
	private static final String BEGIN_TIME_OPTION = "time.begin";
	private static final String END_TIME_OPTION = "time.begin";
	
	private static final String REDUCE_OPTION = "reduceNo";

	private static final int MAX_TOKEN_LENGTH = 64;       // Throw away tokens longer than this.
	private static final int MIN_DF_DEFAULT = 100;        // Throw away terms with df less than this.
	private static final int MAX_DOC_LENGTH = 512 * 1024 * 1024 * 1024; // Skip document if long than this.
	private static final int MIN_DOC_LENGTH = 10; // Skip document if shorter than this.

	private static class MyMapper extends
			Mapper<LongWritable, FullRevision, Text, PairOfIntLong> {
		
		private static final Text term = new Text();
		private static final PairOfIntLong pair = new PairOfIntLong();
		private MediaWikiProcessor processor;
				
		@Override
		public void setup(Context context) throws IOException {
			String analyzerType = context.getConfiguration().get(PREPROCESSING);
			ANALYZER = AnalyzerFactory.getAnalyzer(analyzerType);
			if (ANALYZER == null) {
				LOG.error("Error: proprocessing type not recognized. Abort " + this.getClass().getName());
				System.exit(1);
			}
			processor = new MediaWikiProcessor();
		}

		@Override
		public void map(LongWritable key, FullRevision doc, Context context) throws IOException,
		InterruptedException {

			context.getCounter(Records.TOTAL).increment(1);

			long revisionId = doc.getRevisionId();
			if (revisionId != 0) {
				context.getCounter(Records.PAGES).increment(1);
				try {
					String content = new String(doc.getText(), StandardCharsets.UTF_8);
					
					// If the document is excessively long, it usually means that something is wrong (e.g., a
					// binary object). Skip so the parsing doesn't choke.
					// As an alternative, we might want to consider putting in a timeout, e.g.,
					// http://stackoverflow.com/questions/2275443/how-to-timeout-a-thread
					if (content.length() > MAX_DOC_LENGTH || content.length() <= MIN_DOC_LENGTH) {
						LOG.info("Skipping " + revisionId + " due to invalid length: " + content.length());
						context.getCounter(Records.SKIPPED).increment(1);
						return;
					}

					String cleaned = processor.getContent(content);
					Object2IntOpenHashMap<String> map = new Object2IntOpenHashMap<>();
					for (String term : AnalyzerUtils.parse(ANALYZER, cleaned)) {
						if (term.length() > MAX_TOKEN_LENGTH) {
							continue;
						}

						if (map.containsKey(term)) {
							map.put(term, map.get(term) + 1);
						} else {
							map.put(term, 1);
						}
					}

					for (Map.Entry<String, Integer> entry : map.entrySet()) {
						term.set(entry.getKey());
						pair.set(1, entry.getValue());
						context.write(term, pair);
					}
				} catch (Exception e) {
					LOG.info("Error caught processing " + revisionId);
					context.getCounter(Records.ERRORS).increment(1);
				}
			}
		}
	}

	private static class MyCombiner extends Reducer<Text, PairOfIntLong, Text, PairOfIntLong> {
		private static final PairOfIntLong output = new PairOfIntLong();

		@Override
		public void reduce(Text key, Iterable<PairOfIntLong> values, Context context)
				throws IOException, InterruptedException {
			int df = 0;
			long cf = 0;
			for (PairOfIntLong pair : values) {
				df += pair.getLeftElement();
				cf += pair.getRightElement();
			}

			output.set(df, cf);
			context.write(key, output);
		}
	}

	private static class MyReducer extends Reducer<Text, PairOfIntLong, Text, PairOfIntLong> {
		private static final PairOfIntLong output = new PairOfIntLong();
		private int dfMin, dfMax;

		@Override
		public void setup(Reducer<Text, PairOfIntLong, Text, PairOfIntLong>.Context context) {
			dfMin = context.getConfiguration().getInt(HADOOP_DF_MIN_OPTION, MIN_DF_DEFAULT);
			dfMax = context.getConfiguration().getInt(HADOOP_DF_MAX_OPTION, Integer.MAX_VALUE);
			LOG.info("dfMin = " + dfMin);
		}

		@Override
		public void reduce(Text key, Iterable<PairOfIntLong> values, Context context)
				throws IOException, InterruptedException {
			int df = 0;
			long cf = 0;
			for (PairOfIntLong pair : values) {
				df += pair.getLeftElement();
				cf += pair.getRightElement();
			}
			if (df < dfMin || df > dfMax) {
				return;
			}
			output.set(df, cf);
			context.write(key, output);
		}
	}

	public static final String INPUT_OPTION = "input";
	public static final String OUTPUT_OPTION = "output";
	public static final String DF_MIN_OPTION = "dfMin";
	public static final String PREPROCESSING = "preprocessing";

	/**
	 * Runs this tool.
	 */
	@SuppressWarnings("static-access")
	public int run(String[] args) throws Exception {
		Options options = new Options();

		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("input path").create(INPUT_OPTION));
		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("output path").create(OUTPUT_OPTION));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("minimum df").create(DF_MIN_OPTION));
		options.addOption(OptionBuilder.withArgName("string " + AnalyzerFactory.getOptions()).hasArg()
				.withDescription("preprocessing").create(PREPROCESSING));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("begin time").create(BEGIN_TIME_OPTION));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("end time").create(END_TIME_OPTION));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("end time").create(REDUCE_OPTION));
		
		CommandLine cmdline;
		CommandLineParser parser = new GnuParser();
		try {
			cmdline = parser.parse(options, args);
		} catch (ParseException exp) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			System.err.println("Error parsing command line: " + exp.getMessage());
			return -1;
		}

		if (!cmdline.hasOption(INPUT_OPTION) || !cmdline.hasOption(OUTPUT_OPTION)
				|| !cmdline.hasOption(PREPROCESSING)) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			return -1;
		}

		String input = cmdline.getOptionValue(INPUT_OPTION);
		String output = cmdline.getOptionValue(OUTPUT_OPTION);
		String preprocessing = cmdline.getOptionValue(PREPROCESSING);

		int reduceNo = 100;
		if (cmdline.hasOption(REDUCE_OPTION)) {
			String reduceNoStr = cmdline.getOptionValue(REDUCE_OPTION);
			try {
				reduceNo = Integer.parseInt(reduceNoStr);
			} catch (NumberFormatException e) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(this.getClass().getName(), options);
				ToolRunner.printGenericCommandUsage(System.out);
				System.err.println("Invalid reduce No. : " + reduceNoStr);
			}
		}
				
		long begin = 0, end = Long.MAX_VALUE;
		if (cmdline.hasOption(BEGIN_TIME_OPTION)) {
			String beginTs = cmdline.getOptionValue(BEGIN_TIME_OPTION);
			try {
				begin = TIME_FORMAT.parseMillis(beginTs);
			} catch (Exception e) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(this.getClass().getName(), options);
				ToolRunner.printGenericCommandUsage(System.out);
				System.err.println("Invalid time format: " + e.getMessage());
			}
		}		
		
		if (cmdline.hasOption(END_TIME_OPTION)) {
			String endTs = cmdline.getOptionValue(END_TIME_OPTION);
			try {
				end = TIME_FORMAT.parseMillis(endTs);
			} catch (Exception e) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(this.getClass().getName(), options);
				ToolRunner.printGenericCommandUsage(System.out);
				System.err.println("Invalid time format: " + e.getMessage());
			}
		}
		
		LOG.info("Tool name: " + BasicComputeTermStats.class.getSimpleName());
		LOG.info(" - input: " + input);
		LOG.info(" - output: " + output);
		LOG.info(" - preprocessing: " + preprocessing);

		getConf().set(PREPROCESSING, preprocessing);

		// skip non-article
		getConf().setBoolean(WikiRevisionInputFormat.SKIP_NON_ARTICLES, false);
		
		// set up range
		getConf().setLong(WikiRevisionInputFormat.REVISION_BEGIN_TIME, begin);
		getConf().setLong(WikiRevisionInputFormat.REVISION_END_TIME, end);
		
		Job job = setup(BasicComputeTermStats.class.getSimpleName() + ":" + input, 
				BasicComputeTermStats.class, input, output,
				WikiFullRevisionJsonInputFormat.class, SequenceFileOutputFormat.class,
				Text.class, PairOfIntLong.class, Text.class, PairOfIntLong.class, 
				MyMapper.class, MyReducer.class, reduceNo);
		
		job.setJarByClass(BasicComputeTermStats.class);

		job.setNumReduceTasks(reduceNo);

		if (cmdline.hasOption(DF_MIN_OPTION)) {
			int dfMin = Integer.parseInt(cmdline.getOptionValue(DF_MIN_OPTION));
			LOG.info(" - dfMin: " + dfMin);
			job.getConfiguration().setInt(HADOOP_DF_MIN_OPTION, dfMin);
		}

		job.setMapperClass(MyMapper.class);
		job.setCombinerClass(MyCombiner.class);
		job.setReducerClass(MyReducer.class);

		FileSystem.get(getConf()).delete(new Path(output), true);

		long startTime = System.currentTimeMillis();
		job.waitForCompletion(true);
		LOG.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

		return 0;
	}

	/**
	 * Dispatches command-line arguments to the tool via the <code>ToolRunner</code>.
	 */
	public static void main(String[] args) throws Exception {
		LOG.info("Running " + BasicComputeTermStats.class.getCanonicalName() + " with args "
				+ Arrays.toString(args));
		ToolRunner.run(new BasicComputeTermStats(), args);
	}
}