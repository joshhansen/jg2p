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
import com.github.steveash.jg2p.PhoneticEncoder
import com.github.steveash.jg2p.PhoneticEncoder.Encoding
import com.github.steveash.jg2p.align.InputReader
import com.github.steveash.jg2p.align.InputRecord
import com.github.steveash.jg2p.phoseq.WordShape
import com.github.steveash.jg2p.rerank.RerankModel
import com.github.steveash.jg2p.util.Fibonacci
import com.github.steveash.jg2p.util.Percent
import com.github.steveash.jg2p.util.ReadWrite
import com.google.common.base.Stopwatch
import com.google.common.collect.ConcurrentHashMultiset
import com.google.common.collect.HashBasedTable
import com.google.common.collect.HashMultiset
import groovyx.gpars.GParsConfig
import groovyx.gpars.GParsPool
import kylm.model.ngram.NgramLM
import org.apache.commons.lang3.StringUtils

import java.util.concurrent.atomic.AtomicInteger

import static org.apache.commons.lang3.StringUtils.left
import static org.apache.commons.lang3.StringUtils.leftPad
import static org.apache.commons.lang3.StringUtils.rightPad

/**
 * Used to play with the failing examples to try and figure out some areas for improvement
 * @author Steve Ash
 */
def rr = RerankModel.from(new File("../resources/dt_rerank_2.pmml"))

//def file = "g014b2b-results.train"
def file = "g014b2b.test"
def inps = InputReader.makePSaurusReader().readFromClasspath(file)
//def inps = InputReader.makeDefaultFormatReader().readFromClasspath(file)

def enc = ReadWrite.readFromFile(PhoneticEncoder.class, new File("../resources/psaur_22_xEps_ww_f3_B.dat"))
enc.setBestAlignments(5)
enc.setBestTaggings(5)
enc.setBestFinal(5)
enc.alignMinScore = Double.NEGATIVE_INFINITY
enc.tagMinScore = Double.NEGATIVE_INFINITY

def lm = ReadWrite.readFromFile(NgramLM.class, new File("../resources/lm_7_kn.dat"))

def goodShapes = ["CCvC", "CCv", "CC", "vCCv", "v", "vC", "vCC", "vCCC", "vCvC", "vv", "vCv", "CCC", "CCCv" ]

Stopwatch watch = Stopwatch.createStarted()
def counts = ConcurrentHashMultiset.create()
def prefixCounts = HashBasedTable.create()
def total = new AtomicInteger(0)
println "Starting..."
new File("../resources/psaur_rerank_out.bad.txt").withPrintWriter { badpw ->
  new File("../resources/psaur_rerank_out.txt").withPrintWriter { pw ->
    pw.println(
        "seq\tword\tphone\tlabel\tA\tB\tA_alignScore\tB_alignScore\tA_tagProb\tB_tagProb\tA_lmScore\tB_lmScore" +
        "\tA_slmScore\tB_slmScore\tA_dupCount\tB_dupCount")
    GParsPool.withPool {
      inps.everyParallel { InputRecord input ->

        def newTotal = total.incrementAndGet()

        def cans = enc.complexEncode(input.xWord)
        List<Encoding> ans = cans.overallResults;
        counts.add("output_" + Fibonacci.prevFibNumber(ans.size()))
        def dups = HashMultiset.create()
        ans.each { dups.add(it.phones) }
        def modeEntry = dups.entrySet().max { it.count }
        int candidatesSameAsMode = dups.entrySet().count { it.count == modeEntry.count }
        if (candidatesSameAsMode == 1) {
          counts.add("unique_mode")
          counts.add("unique_mode_count_" + modeEntry.count)
          if (modeEntry.element == input.yWord.value) {
            counts.add("unique_mode_correct")
          }
        } else {
          counts.add("nonunique_mode_" + candidatesSameAsMode)
          counts.add("nonunique_mode_count_" + modeEntry.count)
        }
        ans = pruneDups(ans)

        def gg = ans.first()
        def alreadyGood = gg.phones == input.yWord.value
        int rank = 1;
        def anyHad = ans.any {
          if (it.phones == input.yWord.value) {
            counts.add("ENCRNK_" + rank)
            return true
          }
          rank += 1
          return false
        }
        if (anyHad) {
          counts.add("IN_TOP_5")
        }
        // lets go through and calculate the stats on how many matching have same shape vs how many not matching
        def wordShape = WordShape.graphShape(input.xWord.value, false)
        def wordSketch = WordShape.graphShape(input.xWord.value, true)
        ans.each {
          def goodAns = it.phones == input.yWord.value
          String goodLabel = (goodAns ? "MATCH" : "NOMATCH")
          def phoneShape = WordShape.phoneShape(it.phones, false)
          def phoneSketch = WordShape.phoneShape(it.phones, true)
          def shapeLabel = (phoneShape == wordShape ? "SHPGOOD" : "SHPBAD")
          def sketchLabel = (phoneSketch == wordSketch ? "SKTGOOD" : "SKTBAD")
          counts.add("SHAPE_${goodLabel}_${shapeLabel}")
          counts.add("SKETCH_${goodLabel}_${sketchLabel}")

          def colPre = (goodAns ? "MATCH_" : "NOMATCH_")
          (1..4).each { len ->
            def ps = left(phoneShape, len)
            def ws = left(wordShape, len)
            def col = colPre + (ps == ws ? "SHPGOOD" : "SHPBAD")
            prefixCounts.put(ps, col, (prefixCounts.get(ps, col) ?: 0) + 1)

            def pk = left(phoneSketch, len)
            def wk = left(wordSketch, len)
            col = colPre + (pk == wk ? "SKTGOOD" : "SKTBAD")
            prefixCounts.put(pk, col, (prefixCounts.get(pk, col) ?: 0) + 1)
          }

          if (goodAns) {
            if (wordShape.startsWith("CvC")) {
              def cvcPrefixMatch = phoneShape.startsWith("CvC")
              counts.add("PREFIX_CVC_" + (cvcPrefixMatch ? "YES" : "NO"))
            }
          }
        }

        // resort by LM
        def totalPerp = 0
        def perpAndEnc = ans.collect {
          def sortOfPerplex = lm.getSentenceProbNormalized(it.phones.toArray(new String[0]))
          assert sortOfPerplex >= 0
          totalPerp += sortOfPerplex
          return [sortOfPerplex, it, sortOfPerplex]
        }
        perpAndEnc = perpAndEnc.collect {
          [((double) it[0]) / ((double) totalPerp), it[1], it[2]]
        }
        perpAndEnc = perpAndEnc.sort { it[0] }
        def lmResults = perpAndEnc

        def pp = perpAndEnc.first()
        def pp2 = perpAndEnc[1]
        def lmBestGood = pp[1].phones == input.yWord.value
        rank = 1;
        perpAndEnc.any {
          if (it[1].phones == input.yWord.value) {
            counts.add("LMRNK_" + rank)
            return true
          }
          rank += 1
          return false
        }

        // now try rescoring based on the perplexity proportion
        perpAndEnc = perpAndEnc.collect {
          [(1.0 - it[0]) * ((Encoding) it[1]).tagProbability(), it[1], perpAndEnc[2]]
        }
        perpAndEnc = perpAndEnc.sort { it[0] }.reverse()
        def slmResults = perpAndEnc
        //println "scaled sort: "
        //perpAndEnc.each { println it }

        def slm = perpAndEnc.first()
        def scaledLmBestGood = slm[1].phones == input.yWord.value
        rank = 1;
        perpAndEnc.any {
          if (it[1].phones == input.yWord.value) {
            counts.add("SLMRNK_" + rank)
            return true
          }
          rank += 1
          return false
        }

        // go through the complex result so we can get a sense of where the answers fall
        int matchAlignCount = cans.alignResults.count {it.rankOfMatchingPhones(input.yWord.value) >= 0}
        def matchAlignTop = cans.alignResults.any {it.rankOfMatchingPhones(input.yWord.value) == 1}
        def anyAlign = cans.alignResults.any {it.rankOfMatchingPhones(input.yWord.value) >= 0}
        cans.alignResults.each {
          def rrr = it.rankOfMatchingPhones(input.yWord.value)
          if (rrr >= 0) {
            counts.add("CANS_MATCHED_RANK_" + rrr)
          }
        }
        counts.add("CANS_ALIGN_COUNT_" + matchAlignCount)
        if (matchAlignTop) {
          counts.add("CANS_TOP_ALIGN")
        }
        if (anyAlign) {
          counts.add("IN_ANY_ALIGN")
        }

        Encoding aa = pp[1]
        Encoding bb = slm[1]
        def aalm = lmResults.find { it[1].phones == aa.phones }.get(0)
        def bblm = lmResults.find { it[1].phones == bb.phones }.get(0)
        def aapb = slmResults.find { it[1].phones == aa.phones }.get(0)
        def bbpb = slmResults.find { it[1].phones == bb.phones }.get(0)
        def bigger = (aa.phones.size() > bb.phones.size() ? "AA_BIGGER" : "BB_BIGGER")

        def rrGood = false;
        if (aa.phones == bb.phones) {
          rrGood = aa.phones == input.yWord.value
        } else {
          // need to choose, run through DT
          counts.add("RR_RUN_COUNT")
          def v = [:]
          v.put("A_alignScore", aa.alignScore)
          v.put("B_alignScore", bb.alignScore)
          v.put("A_tagProb", aa.tagProbability())
          v.put("B_tagProb", bb.tagProbability())
          v.put("A_lmScore", aalm)
          v.put("B_lmScore", bblm)
          v.put("A_slmScore", aapb)
          v.put("B_slmScore", bbpb)
          v.put("bigger", bigger)
          v.put("A_dupCount", dups.count(aa.phones))
          v.put("B_dupCount", dups.count(bb.phones))

          def rrResult = rr.label(v)
          if (rrResult == "LM") {
            rrGood = aa.phones == input.yWord.value
          } else {
            assert rrResult == "SLM"
            rrGood = bb.phones == input.yWord.value
          }
        }

        // word\tphone\tlabel\tA\tB\talign\tA_alignScore\tB_alignScore\tA-B_alignScore\tA_tagProb\tB_tagProb\tA-B_tagProb\tA_lmScore\tB_lmScore\tA-B_lmScore\tA_slmScore\tB_slmScore\tA-B_slmScore\tbigger\tA_dupCount\tB_dupCount\tA-B_dupCount

        if (scaledLmBestGood ^ lmBestGood || !anyHad) {
          def msg = input.xWord.asSpaceString + "\t" + input.yWord.value.join("|") + "\t" +
                    (lmBestGood ? "LM" : scaledLmBestGood ? "SLM" : "XXX") + "\t" +
                    aa.phones.join("|") + "\t" + bb.phones.join("|") + "\t" +
                    aa.alignScore + "\t" + bb.alignScore + "\t" +
                    (aa.alignScore - bb.alignScore) + "\t" +
                    aa.tagProbability() + "\t" + bb.tagProbability() + "\t" +
                    (aa.tagProbability() - bb.tagProbability()) + "\t" +
                    aalm + "\t" + bblm + "\t" + (aalm - bblm) + "\t" +
                    aapb + "\t" + bbpb + "\t" + (aapb - bbpb) + "\t" +
                    bigger + "\t" +
                    dups.count(aa.phones) + "\t" + dups.count(bb.phones) + "\t" +
                    (dups.count(aa.phones) - dups.count(bb.phones)) + "\t"

          aa.phones.toSet().each { msg += "\t" + "A_" + it }
          aa.phones.toSet().each { msg += "\t" + "B_" + it }
          if (!anyHad) {
            badpw.println(msg)
          } else {
            pw.println(msg)
          }
        }

        counts.add("BINNED_" + bin(alreadyGood, "ENC") + "_" + bin(lmBestGood, "LM") +
                   "_" + bin(scaledLmBestGood, "SCALED"))

        if (alreadyGood) {
          counts.add("ENC")
        }
        if (lmBestGood) {
          counts.add("LM")
        }
        if (rrGood) {
          counts.add("RR")
        }
        if (scaledLmBestGood) {
          counts.add("SCALED")
        }

        if (newTotal % 5000 == 0) {
          println "Completed " + newTotal + " of " + inps.size()
        }
        return true;
      }
    }
  }
}
watch.stop()
GParsConfig.shutdown()

new File("../resources/prefix-counts.txt").withPrintWriter { pw ->
  def cols = prefixCounts.columnKeySet().sort()
  pw.println("prefix," + cols.join(","))
  prefixCounts.rowKeySet().each { rowKey ->
    String line = rowKey
    cols.each { colKey ->
      line += "," + (prefixCounts.get(rowKey, colKey) ?: 0)
    }
    pw.println(line)
  }
}

def tot = total.get()
println "Total $tot"
println "Eval took " + watch
Collection<String> elems = counts.entrySet().collect { it.element }.sort()
def max = elems.max { it.length() }.length()
for (Object elem : elems) {
  def thisCount = counts.count(elem)
  println StringUtils.leftPad(elem, max, ' ' as String) + "  -  " + thisCount + "  " + Percent.print(thisCount, tot)
}

Object bin(boolean cond, String label) {
  if (cond) {
    return label
  }
  return "NOT" + label
}

List<Encoding> pruneDups(List<Encoding> encodings) {
  def result = []
  def seen = [].toSet()
  result << encodings.first()
  seen.add(encodings.first().phones)

  for (int i = 1; i < encodings.size(); i++) {
    def cand = encodings.get(i)
    if (seen.add(cand.phones)) {
      result << cand
    }
  }
  return result
}