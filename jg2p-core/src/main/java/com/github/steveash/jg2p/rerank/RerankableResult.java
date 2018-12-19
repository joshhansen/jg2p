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

package com.github.steveash.jg2p.rerank;

import com.github.steveash.jg2p.PhoneticEncoder;
import com.github.steveash.jg2p.PhoneticEncoder.Encoding;
import com.github.steveash.jg2p.PhoneticEncoder.Result;
import com.github.steveash.jg2p.lm.LangModel;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Container for phonetic encoder result that calculates all of the info that we need for reranking (at train and
 * test time)
 * @author Steve Ash
 */
public class RerankableResult {

  private final Result encoderResult;
  private final Map<Encoding, Double> encodingToLangModelScore;
  private HashMultiset<List<String>> phoneSequenceCount;
  private List<String> modePhones;
  private boolean hasUniqueMode;
  final boolean isValid;

  public RerankableResult(Result encoderResult, LangModel langModel) {
    this.encoderResult = encoderResult;
    this.encodingToLangModelScore = new IdentityHashMap<>(encoderResult.overallResults.size());

    // calculate the lang model score for all of the entries
    for(PhoneticEncoder.Encoding it : encoderResult.overallResults) {
    	encodingToLangModelScore.put(it, langModel.score(it));
    }

    List<PhoneticEncoder.Encoding>overall = encoderResult.overallResults;
    if (overall.size() <= 0) {
      this.isValid = false;
      return;
    }
    this.phoneSequenceCount = HashMultiset.create();
    
//    overall.each { phoneSequenceCount.add(it.phones) }
    
    for(PhoneticEncoder.Encoding it : overall) {
    	phoneSequenceCount.add(it.phones);
    }
    
    Multiset.Entry<List<String>> modeEntry = null;
    for(Multiset.Entry<List<String>> it : phoneSequenceCount.entrySet()) {
    	if(modeEntry == null || it.getCount() > modeEntry.getCount()) {
    		modeEntry = it;
    	}
    }
    assert(modeEntry != null);
    int candidatesSameAsMode = 0;
    for(Multiset.Entry<List<String>> it : phoneSequenceCount.entrySet()) {
    	if(it.getCount() == modeEntry.getCount()) {
    		candidatesSameAsMode++;
    	}
    }
    //int candidatesSameAsMode = phoneSequenceCount.entrySet().count { it.count == modeEntry.count }
    this.modePhones = modeEntry.getElement();
    this.hasUniqueMode = (candidatesSameAsMode == 1);
    this.isValid = true;
  }

  Result encoderResult() {
    return this.encoderResult;
  }

  int overallResultCount() {
    return encoderResult.overallResults.size();
  }

  Encoding encodingAtIndex(int index) {
    return encoderResult.overallResults.get(index);
  }

  @Nullable
  RerankableEntry firstEntryFor(List<String> phones) {
	  int matchingIndex = -1;
	  
	  for(int i = 0; i < encoderResult.overallResults.size(); i++) {
		  if(phones == encoderResult.overallResults.get(i).phones) {
			  matchingIndex = i;
			  break;
		  }
	  }
//    def matchingIndex = encoderResult.overallResults.findIndexOf { phones == it.phones }
    if (matchingIndex < 0) {
      return null;
    }
    return entryAtOverallIndex(matchingIndex);
  }

  RerankableEntry entryAtOverallIndex(int matchingIndex) {
	  Encoding matching = encoderResult.overallResults.get(matchingIndex);
//    def matching = encoderResult.overallResults[matchingIndex]
//    assert matching != null
	  assert(matching != null);
//    def bestDupCount = phoneSequenceCount.count(matching.phones)
    
	  final int bestDupCount = phoneSequenceCount.count(matching.phones);
    		
//    def bestUniqueMode = hasUniqueMode && matching.phones == modePhones
	  
	  final boolean bestUniqueMode = hasUniqueMode && matching.phones == modePhones;
	  
//    def bestLm = encodingToLangModelScore.get(matching)
	  
	  final Double bestLm = encodingToLangModelScore.get(matching);
	  
//    assert bestLm != null // how is there an encoding now that we didn't have before?
	  
	  assert(bestLm != null);
	  
    return new RerankableEntry(matching, bestUniqueMode, bestDupCount, bestLm, matchingIndex);
  }

  @Override
  public String toString() {
    return "RerankableResult{" +
           "encoderResult=" + encoderResult +
           ", encodingToLangModelScore=" + encodingToLangModelScore +
           ", phoneSequenceCount=" + phoneSequenceCount +
           ", modePhones=" + modePhones +
           ", hasUniqueMode=" + hasUniqueMode +
           ", isValid=" + isValid +
           '}';
  }
}
