/* LanguageTool, a natural language style checker 
 * Copyright (C) 2006 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.tagging.uk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.languagetool.AnalyzedToken;
import org.languagetool.JLanguageTool;
import org.languagetool.tagging.BaseTagger;
import org.languagetool.tagging.TaggedWord;
import org.languagetool.tagging.WordTagger;

/** 
 * Ukrainian part-of-speech tagger.
 * See README for details, the POS tagset is described in tagset.txt
 * 
 * @author Andriy Rysin
 */
public class UkrainianTagger extends BaseTagger {
  private static final Pattern GENDER_CONJ_REGEX = Pattern.compile("(noun|adjp?|numr):(.:v_...).*");
  private static final String NV_TAG = ":nv";
  private static final String COMPB_TAG = ":compb";
//  private static final String V_U_TAG = ":v-u";
  private static final Pattern EXTRA_TAGS = Pattern.compile("(:(v-u|np|ns|bad|slang|rare))+");
//  private static final Pattern EXTRA_TAGS_DOUBLE = Pattern.compile("(:(nv|np|ns))+");
  private static final String DEBUG_COMPOUNDS_PROPERTY = "org.languagetool.tagging.uk.UkrainianTagger.debugCompounds";
  private static final String TAG_ANIM = ":anim";
  
  private static final Pattern MNP_NAZ_REGEX = Pattern.compile(".*:[mnp]:v_naz.*");
  private static final Pattern MNP_ZNA_REGEX = Pattern.compile(".*:[mnp]:v_zna.*");
  private static final Pattern MNP_ROD_REGEX = Pattern.compile(".*:[mnp]:v_rod.*");
  private static final Pattern NOUN_SING_V_ROD_REGEX = Pattern.compile("noun:[mfn]:v_rod.*");
  private static final Pattern NOUN_V_NAZ_REGEX = Pattern.compile("noun:.:v_naz.*");
  private static final Pattern SING_REGEX_F = Pattern.compile(":[mfn]:");
  
//  private static final String VERB_TAG_FOR_REV_IMPR = IPOSTag.verb.getText()+":rev:impr";
//  private static final String VERB_TAG_FOR_IMPR = IPOSTag.verb.getText()+":impr";
  private static final String ADJ_TAG_FOR_PO_ADV_MIS = IPOSTag.adj.getText() + ":m:v_mis";
  private static final String ADJ_TAG_FOR_PO_ADV_NAZ = IPOSTag.adj.getText() + ":m:v_naz";
  // full latin number regex: M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})
  private static final Pattern NUMBER = Pattern.compile("[+-]?[€₴\\$]?[0-9]+(,[0-9]+)?([-–—][0-9]+(,[0-9]+)?)?(%|°С?)?|(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");
  private static final Pattern DATE = Pattern.compile("[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}");
  private static final String stdNounTag = IPOSTag.noun.getText() + ":.:v_";
  private static final int stdNounTagLen = stdNounTag.length();
  private static final Pattern stdNounTagRegex = Pattern.compile(stdNounTag + ".*");
//  private static final Pattern stdNounNvTagRegex = Pattern.compile(IPOSTag.noun.getText() + ".*:nv.*");
  private static final Set<String> dashPrefixes;
  private static final Set<String> leftMasterSet;
  private static final Set<String> cityAvenue = new HashSet<>(Arrays.asList("сіті", "авеню", "стріт", "штрассе"));
  private static final Map<String, String> rightPartsWithLeftTagMap = new HashMap<>();
  private static final Set<String> slaveSet;
  
  public static final Map<String, String> VIDMINKY_MAP;
  private static final Map<String, List<String>> NUMR_ENDING_MAP;
  private BufferedWriter compoundUnknownDebugWriter;
  private BufferedWriter compoundTaggedDebugWriter;

  static {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("v_naz", "називний");
    map.put("v_rod", "родовий");
    map.put("v_dav", "давальний");
    map.put("v_zna", "знахідний");
    map.put("v_oru", "орудний");
    map.put("v_mis", "місцевий");
    map.put("v_kly", "кличний");
    VIDMINKY_MAP = Collections.unmodifiableMap(map);

    Map<String, List<String>> map2 = new HashMap<>();
    map2.put("й", Arrays.asList(":m:v_naz", ":m:v_zna"));
    map2.put("го", Arrays.asList(":m:v_rod", ":m:v_zna", ":n:v_rod"));
    map2.put("му", Arrays.asList(":m:v_dav", ":m:v_mis", ":n:v_dav", ":n:v_mis", ":f:v_zna"));  // TODO: depends on the last digit
//    map2.put("им", Arrays.asList(":m:v_oru", ":n:v_oru"));
//    map2.put("ім", Arrays.asList(":m:v_mis", ":n:v_mis"));
//    map2.put("та", Arrays.asList(":f:v_naz"));
//    map2.put("тої", Arrays.asList(":f:v_rod"));
//    map2.put("тій", Arrays.asList(":f:v_dav", ":f:v_mis"));
//    map2.put("ту", Arrays.asList(":f:v_zna"));
//    map2.put("тою", Arrays.asList(":f:v_oru"));
    map2.put("те", Arrays.asList(":n:v_naz", ":n:v_zna"));
    map2.put("ті", Arrays.asList(":p:v_naz", ":p:v_zna"));
//    map2.put("тих", Arrays.asList(":p:v_rod", ":p:v_zna"));
    NUMR_ENDING_MAP = Collections.unmodifiableMap(map2);
    
    rightPartsWithLeftTagMap.put("бо", "(verb(:rev)?:impr|.*pron|noun|adv|excl|part|predic).*");
    rightPartsWithLeftTagMap.put("но", "(verb(:rev)?:(impr|futr)|excl).*"); 
    rightPartsWithLeftTagMap.put("от", "(.*pron|adv|part).*"); 
    rightPartsWithLeftTagMap.put("то", "(.*pron|noun|adv|part|conj).*"); 
    rightPartsWithLeftTagMap.put("таки", "(verb(:rev)?:(futr|past|pres)|.*pron|noun|part|predic|insert).*"); 
    
    dashPrefixes = loadSet("/uk/dash_prefixes.txt");
    leftMasterSet = loadSet("/uk/dash_left_master.txt");
    slaveSet = loadSet("/uk/dash_slaves.txt");
    // TODO: "бабуся", "лялька", "рятівник" - not quite slaves, could be masters too
  }

  private static Set<String> loadSet(String path) {
    InputStream is = JLanguageTool.getDataBroker().getFromResourceDirAsStream(path);
    Set<String> result = new HashSet<>();
    try (Scanner scanner = new Scanner(is,"UTF-8")) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        result.add(line);
      }
      return result;
    }
  }

  @Override
  public final String getFileName() {
    return "/uk/ukrainian.dict";
  }

  @Override
  public String getManualAdditionsFileName() {
    return "/uk/added.txt";
  }

  public UkrainianTagger() {
    super();
    setLocale(new Locale("uk", "UA"));
    dontTagLowercaseWithUppercase();
    
    if( Boolean.valueOf( System.getProperty(DEBUG_COMPOUNDS_PROPERTY) ) ) {
      debugCompounds();
    }
  }

  @Override
  public List<AnalyzedToken> additionalTags(String word, WordTagger wordTagger) {
    if ( NUMBER.matcher(word).matches() ) {
      List<AnalyzedToken> additionalTaggedTokens  = new ArrayList<>();
      additionalTaggedTokens.add(new AnalyzedToken(word, IPOSTag.number.getText(), word));
      return additionalTaggedTokens;
    }

    if ( DATE.matcher(word).matches() ) {
      List<AnalyzedToken> additionalTaggedTokens  = new ArrayList<>();
      additionalTaggedTokens.add(new AnalyzedToken(word, IPOSTag.date.getText(), word));
      return additionalTaggedTokens;
    }

    if ( word.contains("-") ) {
      List<AnalyzedToken> guessedCompoundTags = guessCompoundTag(word);
      debug_compound_tagged_write(guessedCompoundTags);
      
      return guessedCompoundTags;
    }
    
    return null;
  }

  @Nullable
  private List<AnalyzedToken> guessCompoundTag(String word) {
    int dashIdx = word.lastIndexOf('-');
    if( dashIdx == 0 || dashIdx == word.length() - 1 )
      return null;

    int firstDashIdx = word.indexOf('-');
    if( dashIdx != firstDashIdx )
      return null;

    String leftWord = word.substring(0, dashIdx);
    String rightWord = word.substring(dashIdx + 1);

    List<TaggedWord> leftWdList = wordTagger.tag(leftWord);
    String leftLowerCase = leftWord.toLowerCase(conversionLocale);
    if( ! leftWord.equals(leftLowerCase)) {
      leftWdList.addAll(wordTagger.tag(leftLowerCase));
    }

    if( rightPartsWithLeftTagMap.containsKey(rightWord) ) {
      if( leftWdList.isEmpty() )
        return null;

      String leftTagRegex = rightPartsWithLeftTagMap.get(rightWord);
      
      List<AnalyzedToken> leftAnalyzedTokens = asAnalyzedTokenListForTaggedWords(leftWord, leftWdList);
      List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(leftAnalyzedTokens.size());
      for (AnalyzedToken analyzedToken : leftAnalyzedTokens) {
        String posTag = analyzedToken.getPOSTag();
        if( posTag.matches(leftTagRegex) ) {
          newAnalyzedTokens.add(new AnalyzedToken(word, posTag, analyzedToken.getLemma()));
        }
      }
      
      return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
    }

    if( leftWord.equalsIgnoreCase("по") && rightWord.endsWith("ськи") ) {
      rightWord += "й";
    }
    
    List<TaggedWord> rightWdList = wordTagger.tag(rightWord);
    if( rightWdList.isEmpty() )
      return null;

    List<AnalyzedToken> rightAnalyzedTokens = asAnalyzedTokenListForTaggedWords(rightWord, rightWdList);

    if( leftWord.equalsIgnoreCase("по") ) {
      if( rightWord.endsWith("ому") ) {
        return poAdvMatch(word, rightAnalyzedTokens, ADJ_TAG_FOR_PO_ADV_MIS);
      }
      else if( rightWord.endsWith("ський") ) {
        return poAdvMatch(word, rightAnalyzedTokens, ADJ_TAG_FOR_PO_ADV_NAZ);
      }
      return null;
    }

    if( NUMBER.matcher(leftWord).matches() ) {
      List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(rightAnalyzedTokens.size());
      // e.g. 101-го
      if( NUMR_ENDING_MAP.containsKey(rightWord) ) {
        List<String> tags = NUMR_ENDING_MAP.get(rightWord);
        for (String tag: tags) {
          // TODO: shall it be numr or adj?
          newAnalyzedTokens.add(new AnalyzedToken(word, IPOSTag.adj.getText()+tag, leftWord + "-" + "й"));
        }
      }
      else {
        // e.g. 100-річному
        for (AnalyzedToken analyzedToken : rightAnalyzedTokens) {
          if( analyzedToken.getPOSTag().startsWith(IPOSTag.adj.getText()) ) {
            newAnalyzedTokens.add(new AnalyzedToken(word, analyzedToken.getPOSTag(), leftWord + "-" + analyzedToken.getLemma()));
          }
        }
      }
      return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
    }

    if( dashPrefixes.contains( leftWord ) || dashPrefixes.contains( leftWord.toLowerCase() ) ) {
      return getNvPrefixNounMatch(word, rightAnalyzedTokens, leftWord);
    }

    if( word.startsWith("пів-") && Character.isUpperCase(word.charAt(4)) ) {
      List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(rightAnalyzedTokens.size());
      
      for (AnalyzedToken rightAnalyzedToken : rightAnalyzedTokens) {
        String rightPosTag = rightAnalyzedToken.getPOSTag();

        if( rightPosTag == null )
          continue;

        if( NOUN_SING_V_ROD_REGEX.matcher(rightPosTag).matches() ) {
          for(String vid: VIDMINKY_MAP.keySet()) {
            if( vid.equals("v_kly") )
              continue;
            String posTag = rightPosTag.replace("v_rod", vid);
            newAnalyzedTokens.add(new AnalyzedToken(word, posTag, word));
          }
        }
      }

      return newAnalyzedTokens;
    }

    if( Character.isUpperCase(leftWord.charAt(0)) && cityAvenue.contains(rightWord) ) {
      if( leftWdList.isEmpty() )
        return null;
      
      List<AnalyzedToken> leftAnalyzedTokens = asAnalyzedTokenListForTaggedWords(leftWord, leftWdList);
      return cityAvenueMatch(word, leftAnalyzedTokens);
    }

    if( ! leftWdList.isEmpty() ) {
      List<AnalyzedToken> leftAnalyzedTokens = asAnalyzedTokenListForTaggedWords(leftWord, leftWdList);

      List<AnalyzedToken> tagMatch = tagMatch(word, leftAnalyzedTokens, rightAnalyzedTokens);
      if( tagMatch != null ) {
        return tagMatch;
      }
    }

    if( leftWord.endsWith("о") ) {
      return oAdjMatch(word, rightAnalyzedTokens, leftWord);
    }

    debug_compound_unknown_write(word);
    
    return null;
  }

  private List<AnalyzedToken> cityAvenueMatch(String word, List<AnalyzedToken> leftAnalyzedTokens) {
    List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(leftAnalyzedTokens.size());
    
    for (AnalyzedToken analyzedToken : leftAnalyzedTokens) {
      String posTag = analyzedToken.getPOSTag();
      if( NOUN_V_NAZ_REGEX.matcher(posTag).matches() ) {
        newAnalyzedTokens.add(new AnalyzedToken(word, posTag.replaceFirst("v_naz", "nv"), word));
      }
    }
    
    return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
  }
  
  private List<AnalyzedToken> tagMatch(String word, List<AnalyzedToken> leftAnalyzedTokens, List<AnalyzedToken> rightAnalyzedTokens) {
    List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>();
    List<AnalyzedToken> newAnalyzedTokensAnimInanim = new ArrayList<>();
    
    String animInanimNotTagged = null;
    
    for (AnalyzedToken leftAnalyzedToken : leftAnalyzedTokens) {
      String leftPosTag = leftAnalyzedToken.getPOSTag();
      
      if( leftPosTag == null )
        continue;

      String leftPosTagExtra = "";
      boolean leftNv = false;

      if( leftPosTag.contains(NV_TAG) ) {
        leftNv = true;
        leftPosTag = leftPosTag.replace(NV_TAG, "");
      }

      Matcher matcher = EXTRA_TAGS.matcher(leftPosTag);
      if( matcher.find() ) {
        leftPosTagExtra += matcher.group();
        leftPosTag = matcher.replaceAll("");
      }
      if( leftPosTag.contains(COMPB_TAG) ) {
        leftPosTag = leftPosTag.replace(COMPB_TAG, "");
      }

      for (AnalyzedToken rightAnalyzedToken : rightAnalyzedTokens) {
        String rightPosTag = rightAnalyzedToken.getPOSTag();
        
        if( rightPosTag == null )
          continue;

        String extraNvTag = "";
        boolean rightNv = false;
        if( rightPosTag.contains(NV_TAG) ) {
          rightNv = true;
          
          if( leftNv ) {
            extraNvTag += NV_TAG;
          }
        }

        Matcher matcherR = EXTRA_TAGS.matcher(rightPosTag);
        if( matcherR.find() ) {
          rightPosTag = matcherR.replaceAll("");
        }
        if( rightPosTag.contains(COMPB_TAG) ) {
          rightPosTag = rightPosTag.replace(COMPB_TAG, "");
        }
        
        if (leftPosTag.equals(rightPosTag) 
            && IPOSTag.startsWith(leftPosTag, IPOSTag.numr, IPOSTag.adv, IPOSTag.adj, IPOSTag.excl, IPOSTag.verb) ) {
          newAnalyzedTokens.add(new AnalyzedToken(word, leftPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
        }
        // noun-noun
        else if ( leftPosTag.startsWith(IPOSTag.noun.getText()) && rightPosTag.startsWith(IPOSTag.noun.getText()) ) {
          String agreedPosTag = getAgreedPosTag(leftPosTag, rightPosTag, leftNv);

          if( agreedPosTag == null 
              && rightPosTag.startsWith("noun:m:v_naz")
              && isMinMax(rightAnalyzedToken.getToken()) ) {
            agreedPosTag = leftPosTag;
          }

          if( agreedPosTag == null && ! isSameAnimStatus(leftPosTag, rightPosTag) ) {

            agreedPosTag = tryAnimInanim(leftPosTag, rightPosTag, leftAnalyzedToken.getLemma(), rightAnalyzedToken.getLemma(), leftNv, rightNv);
            
            if( agreedPosTag == null ) {
              animInanimNotTagged = leftPosTag.contains(":anim") ? "anim-inanim" : "inanim-anim";
            }
            else {
              newAnalyzedTokensAnimInanim.add(new AnalyzedToken(word, agreedPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
              continue;
            }
          }
          
          if( agreedPosTag != null ) {
            newAnalyzedTokens.add(new AnalyzedToken(word, agreedPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
          }
        }
        // numr-numr: один-два
        else if ( leftPosTag.startsWith(IPOSTag.numr.getText()) && rightPosTag.startsWith(IPOSTag.numr.getText()) ) {
            String agreedPosTag = getNumAgreedPosTag(leftPosTag, rightPosTag, leftNv);
            if( agreedPosTag != null ) {
              newAnalyzedTokens.add(new AnalyzedToken(word, agreedPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
            }
        }
        // noun-numr match
        else if ( IPOSTag.startsWith(leftPosTag, IPOSTag.noun) && IPOSTag.startsWith(rightPosTag, IPOSTag.numr) ) {
          // gender tags match
          String leftGenderConj = getGenderConj(leftPosTag);
          if( leftGenderConj != null && leftGenderConj.equals(getGenderConj(rightPosTag)) ) {
            newAnalyzedTokens.add(new AnalyzedToken(word, leftPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
          }
          else {
            // (with different gender tags): сотні (:p:) - дві (:f:)
            String agreedPosTag = getNumAgreedPosTag(leftPosTag, rightPosTag, leftNv);
            if( agreedPosTag != null ) {
              newAnalyzedTokens.add(new AnalyzedToken(word, agreedPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
            }
          }
        }
        // noun-adj match: Буш-молодший, братів-православних, рік-два
        else if( leftPosTag.startsWith(IPOSTag.noun.getText()) 
            && IPOSTag.startsWith(rightPosTag, IPOSTag.adj, IPOSTag.numr) ) {
          String leftGenderConj = getGenderConj(leftPosTag);
          if( leftGenderConj != null && leftGenderConj.equals(getGenderConj(rightPosTag)) ) {
            newAnalyzedTokens.add(new AnalyzedToken(word, leftPosTag + extraNvTag + leftPosTagExtra, leftAnalyzedToken.getLemma() + "-" + rightAnalyzedToken.getLemma()));
          }
        }
      }
    }
    
    if( newAnalyzedTokens.isEmpty() ) {
      newAnalyzedTokens = newAnalyzedTokensAnimInanim;
    }

    if( animInanimNotTagged != null && newAnalyzedTokens.isEmpty() ) {
      debug_compound_unknown_write(word + " " + animInanimNotTagged);
    }
    
    return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
  }

  private static boolean isMinMax(String rightToken) {
    return rightToken.equals("максимум")
        || rightToken.equals("мінімум");
  }

  private String tryAnimInanim(String leftPosTag, String rightPosTag, String leftLemma, String rightLemma, boolean leftNv, boolean rightNv) {
    String agreedPosTag = null;
    
    // підприємство-банкрут
    if( leftMasterSet.contains(leftLemma) ) {
      if( leftPosTag.contains(TAG_ANIM) ) {
        rightPosTag = rightPosTag.concat(TAG_ANIM);
      }
      else {
        rightPosTag = rightPosTag.replace(TAG_ANIM, "");
      }
      
      agreedPosTag = getAgreedPosTag(leftPosTag, rightPosTag, leftNv);
      
      if( agreedPosTag == null ) {
        if (! leftPosTag.contains(TAG_ANIM)) {
          if (MNP_ZNA_REGEX.matcher(leftPosTag).matches() && MNP_NAZ_REGEX.matcher(rightPosTag).matches()
              && ! leftNv && ! rightNv ) {
            agreedPosTag = leftPosTag;
          }
        }
        else {
          if (MNP_ZNA_REGEX.matcher(leftPosTag).matches() && MNP_ROD_REGEX.matcher(rightPosTag).matches()
              && ! leftNv && ! rightNv ) {
            agreedPosTag = leftPosTag;
          }
        }
      }
      
    }
    // сонях-красень
    else if ( slaveSet.contains(rightLemma) ) {
      rightPosTag = rightPosTag.replace(":anim", "");
      agreedPosTag = getAgreedPosTag(leftPosTag, rightPosTag, false);
      if( agreedPosTag == null ) {
        if (! leftPosTag.contains(TAG_ANIM)) {
          if (MNP_ZNA_REGEX.matcher(leftPosTag).matches() && MNP_NAZ_REGEX.matcher(rightPosTag).matches()
              && getNum(leftPosTag).equals(getNum(rightPosTag))
              && ! leftNv && ! rightNv ) {
            agreedPosTag = leftPosTag;
          }
        }
      }
    }
    // красень-сонях
    else if ( slaveSet.contains(leftLemma) ) {
      leftPosTag = leftPosTag.replace(":anim", "");
      agreedPosTag = getAgreedPosTag(rightPosTag, leftPosTag, false);
      if( agreedPosTag == null ) {
        if (! rightPosTag.contains(TAG_ANIM)) {
          if (MNP_ZNA_REGEX.matcher(rightPosTag).matches() && MNP_NAZ_REGEX.matcher(leftPosTag).matches()
              && getNum(leftPosTag).equals(getNum(rightPosTag))
              && ! leftNv && ! rightNv ) {
            agreedPosTag = rightPosTag;
          }
        }
      }
    }
    // else
    // рослин-людожерів, слалому-гіганту, місяця-князя, депутатів-привидів
    
    return agreedPosTag;
  }

  // right part is numr
  private String getNumAgreedPosTag(String leftPosTag, String rightPosTag, boolean leftNv) {
    String agreedPosTag = null;
    
    if( leftPosTag.contains(":p:") && SING_REGEX_F.matcher(rightPosTag).find()
        || SING_REGEX_F.matcher(leftPosTag).find() && rightPosTag.contains(":p:")) {
      if( getConj(leftPosTag).equals(getConj(rightPosTag)) ) {
        agreedPosTag = leftPosTag;
      }
    }
    return agreedPosTag;
  }

  @Nullable
  private String getAgreedPosTag(String leftPosTag, String rightPosTag, boolean leftNv) {
    if( isPlural(leftPosTag) && ! isPlural(rightPosTag)
        || ! isPlural(leftPosTag) && isPlural(rightPosTag) )
      return null;
    
    if( ! isSameAnimStatus(leftPosTag, rightPosTag) )
      return null;
    
    if( stdNounTagRegex.matcher(leftPosTag).matches() ) {
      if (stdNounTagRegex.matcher(rightPosTag).matches()) {
        String substring1 = leftPosTag.substring(stdNounTagLen, stdNounTagLen + 3);
        String substring2 = rightPosTag.substring(stdNounTagLen, stdNounTagLen + 3);
        if( substring1.equals(substring2) ) {
          if( leftNv )
            return rightPosTag;

          return leftPosTag;
        }
      }
    }

    return null;
  }

  private static boolean isSameAnimStatus(String leftPosTag, String rightPosTag) {
    return leftPosTag.contains(TAG_ANIM) && rightPosTag.contains(TAG_ANIM)
        || ! leftPosTag.contains(TAG_ANIM) && ! rightPosTag.contains(TAG_ANIM);
  }

  private static boolean isPlural(String posTag) {
    return posTag.startsWith("noun:p:");
  }

  private List<AnalyzedToken> oAdjMatch(String word, List<AnalyzedToken> analyzedTokens, String leftWord) {
    List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(analyzedTokens.size());
    
    for (AnalyzedToken analyzedToken : analyzedTokens) {
      String posTag = analyzedToken.getPOSTag();
      if( posTag.startsWith( IPOSTag.adj.getText() ) ) {
        newAnalyzedTokens.add(new AnalyzedToken(word, posTag, leftWord + "-" + analyzedToken.getLemma()));
      }
    }
    
    return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
  }

  private List<AnalyzedToken> getNvPrefixNounMatch(String word, List<AnalyzedToken> analyzedTokens, String leftWord) {
    List<AnalyzedToken> newAnalyzedTokens = new ArrayList<>(analyzedTokens.size());
    
    for (AnalyzedToken analyzedToken : analyzedTokens) {
      String posTag = analyzedToken.getPOSTag();
      if( posTag.startsWith( IPOSTag.noun.getText() ) ) {
        newAnalyzedTokens.add(new AnalyzedToken(word, posTag, leftWord + "-" + analyzedToken.getLemma()));
      }
    }
    
    return newAnalyzedTokens.isEmpty() ? null : newAnalyzedTokens;
  }

  @Nullable
  private List<AnalyzedToken> poAdvMatch(String word, List<AnalyzedToken> analyzedTokens, String adjTag) {
    
    for (AnalyzedToken analyzedToken : analyzedTokens) {
      String posTag = analyzedToken.getPOSTag();
      if( posTag.startsWith( adjTag ) ) {
        return Arrays.asList(new AnalyzedToken(word, IPOSTag.adv.getText(), word));
      }
    }
    
    return null;
  }


  @Nullable
  private static String getGenderConj(String posTag) {
    Matcher pos4matcher = GENDER_CONJ_REGEX.matcher(posTag);
    if( pos4matcher.matches() )
      return pos4matcher.group(2);

    return null;
  }

//  private static String getNumAndConj(String posTag) {
//    Matcher pos4matcher = GENDER_CONJ_REGEX.matcher(posTag);
//    if( pos4matcher.matches() ) {
//      String group = pos4matcher.group(2);
//      if( group.charAt(0) != 'p' ) {
//        group = "s" + group.substring(1);
//      }
//      return group;
//    }
//
//    return null;
//  }

  @Nullable
  private static String getNum(String posTag) {
    Matcher pos4matcher = Pattern.compile("(noun|adjp?|numr):(.):v_.*").matcher(posTag);
    if( pos4matcher.matches() ) {
      String group = pos4matcher.group(2);
      if( ! group.equals("p") ) {
        group = "s";
      }
      return group;
    }

    return null;
  }

  @Nullable
  private static String getConj(String posTag) {
    Matcher pos4matcher = Pattern.compile("(noun|adjp?|numr):[mfnp]:(v_...).*").matcher(posTag);
    if( pos4matcher.matches() )
      return pos4matcher.group(2);

    return null;
  }


  // methods for debugging compounds

  private void debugCompounds() {
    try {
      Path unknownFile = Paths.get("compounds-unknown.txt");
      Files.deleteIfExists(unknownFile);
      unknownFile = Files.createFile(unknownFile);
      compoundUnknownDebugWriter = Files.newBufferedWriter(unknownFile, Charset.defaultCharset());

      Path taggedFile = Paths.get("compounds-tagged.txt");
      Files.deleteIfExists(taggedFile);
      taggedFile = Files.createFile(taggedFile);
      compoundTaggedDebugWriter = Files.newBufferedWriter(taggedFile, Charset.defaultCharset());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void debug_compound_unknown_write(String word) {
    if( compoundUnknownDebugWriter == null )
      return;
    
    try {
      compoundUnknownDebugWriter.append(word);
      compoundUnknownDebugWriter.newLine();
      compoundUnknownDebugWriter.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void debug_compound_tagged_write(List<AnalyzedToken> guessedCompoundTags) {
    if( compoundTaggedDebugWriter == null || guessedCompoundTags == null )
      return;
    
    try {
      String prevToken = "";
      String prevLemma = "";
      for (AnalyzedToken analyzedToken : guessedCompoundTags) {
        String token = analyzedToken.getToken();
        
        boolean firstTag = false;
        if (! prevToken.equals(token)) {
          if( prevToken.length() > 0 ) {
            compoundTaggedDebugWriter.append(";  ");
            prevLemma = "";
          }
          compoundTaggedDebugWriter.append(token).append(" ");
          prevToken = token;
          firstTag = true;
        }
        
        String lemma = analyzedToken.getLemma();

        if (! prevLemma.equals(lemma)) {
          if( prevLemma.length() > 0 ) {
            compoundTaggedDebugWriter.append(", ");
          }
          compoundTaggedDebugWriter.append(lemma); //.append(" ");
          prevLemma = lemma;
          firstTag = true;
        }

        compoundTaggedDebugWriter.append(firstTag ? " " : "|").append(analyzedToken.getPOSTag());
        firstTag = false;
      }
      compoundTaggedDebugWriter.newLine();
      compoundTaggedDebugWriter.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  
}
