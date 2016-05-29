/*
 * Copyright 2016 Steve Ash
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

package com.github.steveash.jg2p.seq

import cc.mallet.types.Alphabet
import cc.mallet.types.Instance
import cc.mallet.types.TokenSequence
import com.github.steveash.jg2p.Word
import com.github.steveash.jg2p.align.Alignment

/**
 * @author Steve Ash
 */
class NearSyllFeatureTest extends GroovyTestCase {

  void testRelative() {
    def inst = new Instance(new Alignment(Word.fromNormalString("psychology"),
                                          Word.fromNormalString("psychology").getLeftOnlyPairs(), 0.0,
                                          Word.fromNormalString("OONOONONON").value, null)
                                .withGraphemeSyllStarts([0, 3, 6, 8].toSet()), null, null, null)
    def pipe1 = new AlignmentToTokenSequence(new Alphabet(), new Alphabet())
    def pipe2 = new NearSyllFeature(true)
    def result = pipe2.pipe(pipe1.pipe(inst))
    def ts = result.getData() as TokenSequence
    println ts
    assert ts.get(1).getFeatureValue("NEARSYLL_NEXT_s_2") > 0
    assert ts.get(2).getFeatureValue("NEARSYLL_NEXT_y_1") > 0
    assert ts.get(9).getFeatureValue("NEARSYLL_NEXT_y_-1") > 0
  }

  void testRelativePrev() {
    def inst = new Instance(new Alignment(Word.fromNormalString("psychology"),
                                          Word.fromNormalString("psychology").getLeftOnlyPairs(), 0.0,
                                          Word.fromNormalString("OONOONONON").value, null)
                                .withGraphemeSyllStarts([0, 3, 6, 8].toSet()), null, null, null)
    def pipe1 = new AlignmentToTokenSequence(new Alphabet(), new Alphabet())
    def pipe2 = new NearSyllFeature(false)
    def result = pipe2.pipe(pipe1.pipe(inst))
    def ts = result.getData() as TokenSequence
    println ts
    assert ts.get(1).getFeatureValue("NEARSYLL_PREV_s_-1") > 0
    assert ts.get(3).getFeatureValue("NEARSYLL_PREV_c_1") > 0
  }
}
