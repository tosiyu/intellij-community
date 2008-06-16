package com.intellij.openapi.compiler.ex;

import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface CompileContextEx extends CompileContext {
  DependencyCache getDependencyCache();

  @Nullable
  VirtualFile getSourceFileByOutputFile(VirtualFile outputFile);

  void addMessage(CompilerMessage message);

  @NotNull
  Set<VirtualFile> getTestOutputDirectories();
  
  /**
   * the same as FileIndex.isInTestSourceContent(), but takes into account generated output dirs
   */
  boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir);

  void addScope(CompileScope additionalScope);
}
