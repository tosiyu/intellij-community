package com.intellij.structuralsearch;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.psi.PsiManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 11, 2005
 * Time: 10:05:31 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class StructuralSearchTestCase extends IdeaTestCase {
  protected MatchOptions options;
  protected Matcher testMatcher;

  protected void setUp() throws Exception {
    super.setUp();

    testMatcher = new Matcher(myProject);
    options = new MatchOptions();
    options.setLooseMatching( true );
    options.setRecursiveSearch(true);
    PsiManager.getInstance(myProject).setEffectiveLanguageLevel(LanguageLevel.JDK_1_4);
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern, FileType fileType) {
    options.clearVariableConstraints();
    options.setSearchPattern(pattern);
    MatcherImplUtil.transform(options);
    pattern = options.getSearchPattern();
    options.setFileType(fileType);
    return testMatcher.testFindMatches(in,pattern,options,filePattern).size();
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern) {
    return findMatchesCount(in, pattern,filePattern, StdFileTypes.JAVA);
  }

  protected int findMatchesCount(String in, String pattern) {
    return findMatchesCount(in,pattern,false);
  }
}
