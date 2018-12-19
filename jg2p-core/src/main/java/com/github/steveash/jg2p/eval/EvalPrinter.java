/*
 * Copyright 2015 Steve Ash
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.steveash.jg2p.eval;

import com.github.steveash.jg2p.eval.EvalStats.EvalExample;
import com.github.steveash.jg2p.util.Percent;
import com.github.steveash.jg2p.util.SimpleWriter;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Steve Ash
 */
class EvalPrinter {

  private static final Logger log = LoggerFactory.getLogger(EvalPrinter.class);

  public static void writeExamples(File output, EvalStats stats) throws FileNotFoundException {
	  PrintWriter pw = new PrintWriter(output);

      pw.println("word\tedits\trank\tprediction\texpected");
      
      List<EvalExample> sorted = sorted(stats.badCases, new Comparator<EvalExample>() {
		@Override
		public int compare(EvalExample o1, EvalExample o2) {
			return o1.inputWord.compareTo(o2.inputWord);
		}
      });
      
      for(EvalExample bad : sorted) {
    	  pw.println(bad.inputWord + "\t" + bad.edits + "\t" + bad.matchedRank + "\t" +
                  bad.alignedPrediction + "\t" + bad.expectedPhones);
      }
      
      pw.close();
  }
  
  private static <T> List<T> sorted(Set<T> set, Comparator<T> cmp) {
	  List<T> list = new ArrayList<>(set);
	  list.sort(cmp);
	  return list;
  }
  
  private static <T extends Comparable<T>> List<Multiset.Entry<T>> sorted(Multiset<T> multiset) {
	  final List<Multiset.Entry<T>> sorted = new ArrayList<>(multiset.entrySet());
	  sorted.sort(new Comparator<Multiset.Entry<T>>() {
			@Override
			public int compare(Entry<T> o1, Entry<T> o2) {
				return o1.getElement().compareTo(o2.getElement());
			}
		});
	  return sorted;
  }

  public static void printTo(SimpleWriter pw, EvalStats stats, String label) {
	final long totalWords = stats.words.get();
	  
    pw.println(StringUtils.center(" " + label + " ", 80, '*'));
    // histo of word pronunciations
    pw.println("* Histogram of how many pronunciations per word");
    
    
    for(Multiset.Entry<Integer> it : sorted(stats.wordOptionsHisto)) {
    	pw.println(" * Words with ${it.element} variants = " + it.getCount() + "  " + Percent.print(it.getCount(), totalWords));
    }
//    stats.wordOptionsHisto.entrySet().sort { it.element }.each {
//      pw.println(" * Words with ${it.element} variants = " + it.count + "  " + Percent.print(it.count, totalWords))
//    }
    pw.println(StringUtils.repeat('*', 20));
//    stats.resultsSizeHisto.entrySet().sort{it.element}.each {
    for(Multiset.Entry<Integer> it : sorted(stats.resultsSizeHisto)) {
      pw.println(" * Queries with ${it.element} results returned = " + it.getCount() + "  " + Percent.print(it.getCount(), totalWords));
    }
    pw.println(StringUtils.repeat('*', 20));
    pw.println("* Counters");
//    stats.counters.entrySet().sort {it.element}.each {
    for(Multiset.Entry<String> it : sorted(stats.counters)) { 
      pw.println(" * " + it.getElement() + " = " + it.getCount());
    }
    pw.println(StringUtils.repeat('*', 20));
    pw.println("IR metrics for various top-k configurations");
    List<Map.Entry<String, IrStats>> sortedIrStats =
    		sorted(stats.irConfigSetup.asMap().entrySet(), new Comparator<Map.Entry<String,IrStats>>(){
		@Override
		public int compare(Map.Entry<String, IrStats> o1, Map.Entry<String, IrStats> o2) {
			return o1.getKey().compareTo(o2.getKey());
		}
	});
//    stats.irConfigSetup.asMap().entrySet().sort {it.key}.each {
    for(Map.Entry<String,IrStats> it : sortedIrStats) {
      pw.println(String.format(" * " + it.getKey() + " = Prec  %.3f (Max %.3f), Recall  %.3f (Max %.3f)",
                               it.getValue().precision(), it.getValue().precisionMax(),
                               it.getValue().recall(), it.getValue().recallMax()));
    }
    pw.println(StringUtils.repeat('*', 20));
    // final stats at the bottom
    
    final long totalPhones = stats.phones.get();
    pw.println(String.format("* Word  Accuracy: %.4f   (WER %.4f)", stats.wordAccuracy(), stats.wordErrorRate()));
    pw.println(String.format("* Phone Accuracy: %.4f   (PER %.4f)", stats.phoneAccuracy(), stats.phoneErrorRate()));
    pw.println(String.format(" * Word top 1 matched %d of %d", stats.top1CorrectWords.get(), totalWords));
    pw.println(String.format(" * Phone top 1 matched %d of %d", (totalPhones - stats.top1PhoneEdits.get()), totalPhones));
    pw.println(String.format(" * Words the produced zero results %d", stats.zeroResultWords.get()));
    pw.println(String.format(" * Multi-value matches %d of %d (%s)", stats.multiValueMatches.get(),
                             stats.multiValueGroupCount.get(), Percent.print(stats.multiValueMatches.get(),
                                                                             stats.multiValueGroupCount.get())));
  }
}
