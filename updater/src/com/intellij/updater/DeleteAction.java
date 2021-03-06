// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DeleteAction extends PatchAction {
  public DeleteAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
  }

  public DeleteAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  public void doBuildPatchFile(File olderDir, File newerFile, ZipOutputStream patchOutput) {
    // do nothing
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.DELETE, false);
    if (result != null) return result;

    if (myPatch.validateDeletion(getPath()) && toFile.exists() && isModified(toFile)) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.DELETE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.DELETE, ValidationResult.Option.KEEP};
      boolean invalid = getChecksum() == Digester.INVALID;
      ValidationResult.Action action = invalid ? ValidationResult.Action.VALIDATE : ValidationResult.Action.DELETE;
      String message = invalid ? "Unexpected file" : "Modified";
      return new ValidationResult(ValidationResult.Kind.CONFLICT, getPath(), action, message, options);
    }

    return null;
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    return getFile(toDir).exists();
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile);
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Runner.logger().info("Delete action. File: " + toFile.getAbsolutePath());

    // a directory can be deleted only when it does not contain any user's content
    boolean canDelete = true;
    if (Files.isDirectory(toFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> children = Files.list(toFile.toPath())) {
        canDelete = !children.findAny().isPresent();
      }
    }

    if (canDelete) {
      Runner.logger().info("Delete: " + toFile.getAbsolutePath());
      Utils.delete(toFile);
    }
    else {
      Runner.logger().info("Preserved: " + toFile.getAbsolutePath());
    }
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!toFile.exists() || Files.isDirectory(toFile.toPath(), LinkOption.NOFOLLOW_LINKS) || isModified(toFile)) {
      Utils.delete(toFile); // make sure there is no directory remained on this path (may remain from previous 'create' actions
      Utils.copy(backupFile, toFile);
    }
  }
}