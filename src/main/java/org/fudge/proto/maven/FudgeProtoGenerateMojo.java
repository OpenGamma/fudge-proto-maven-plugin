/*
 *  Copyright 2013 Stephen Colebourne
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.fudge.proto.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.tools.ant.DirectoryScanner;
import org.fudgemsg.proto.CommandLine;

/**
 * Maven plugin for running Fudge-Proto.
 * 
 * @goal generate
 * @phase generate-sources
 * 
 * @author Stephen Colebourne
 */
public class FudgeProtoGenerateMojo extends AbstractMojo {

  /**
   * @parameter alias="sourceDir" expression="${project.build.sourceDirectory}"
   * @required
   * @readonly
   */
  private String _sourceDir;
  /**
   * The files to exclude, separated by semicolon, may include star wildcard.
   * @parameter alias="excludes" expression="${fudge.proto.excludes}"
   */
  private String _excludes;
  /**
   * The search directories, separated by semicolon, for finding proto files.
   * @parameter alias="searchDir" expression="${fudge.proto.searchDir}"
   */
  private String _searchDir;
  /**
   * True for verbose/debugging output, defaults to false.
   * @parameter alias="verbose" expression="${fudge.proto.verbose}"
   */
  private boolean _verbose;
  /**
   * True to list all files processed, defaults to false.
   * @parameter alias="listFiles" expression="${fudge.proto.listFiles}"
   */
  private boolean _listFiles;
  /**
   * True to build all files ignoring timestamps.
   * @parameter alias="rebuildAll" expression="${fudge.proto.rebuildAll}"
   */
  private boolean _rebuildAll;
  /**
   * True to write a gitignore file for generated files, defaults to false.
   * @parameter alias="gitIgnore" expression="${fudge.proto.gitIgnore}"
   */
  private boolean _gitIgnore;

  /**
   * True to generate equals methods in output, defaults to true.
   * @parameter alias="equals" expression="${fudge.proto.equals}"
   */
  private boolean _equals = true;
  /**
   * True to generate hashCode methods in output, defaults to true.
   * @parameter alias="hashCode" expression="${fudge.proto.hashCode}"
   */
  private boolean _hashCode = true;
  /**
   * True to generate toString methods in output, defaults to true.
   * @parameter alias="toString" expression="${fudge.proto.toString}"
   */
  private boolean _toString = true;
  /**
   * Expression to use in place of a parameterized context (e.g. FudgeContext.GLOBAL_DEFAULT).
   * @parameter alias="fudgeContext" expression="${fudge.proto.fudgeContext}"
   */
  private String _fudgeContext;
  /**
   * True if fields are mutable by default, false otherwise.
   * @parameter alias="fieldsMutable" expression="${fudge.proto.fieldsMutable}"
   */
  private Boolean _fieldsMutable;
  /**
   * True if fields are required by default, false otherwise.
   * @parameter alias="fieldsRequired" expression="${fudge.proto.fieldsRequired}"
   */
  private Boolean _fieldsRequired;
  /**
   * The file header to add.
   * @parameter alias="fileHeader" expression="${fudge.proto.fileHeader}"
   */
  private String _fileHeader;
  /**
   * The file header to add.
   * @parameter alias="fileFooter" expression="${fudge.proto.fileFooter}"
   */
  private String _fileFooter;

  /**
   * Executes the Fudge-Proto generator.
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (_sourceDir == null) {
      throw new MojoExecutionException("Source directory must not be null");
    }
    if (CommandLine.checkPackages() == false) {
      throw new MojoExecutionException("Invalid classpath");
    }
    
    // build args
    final List<String> args = new ArrayList<String>(10);
    args.add("-d" + _sourceDir);
    args.add("-s" + _sourceDir);
    args.add("-lJava");
    if (_fieldsMutable != null) {
      args.add(_fieldsMutable ? "-fmutable" : "-freadonly");
    }
    if (_fieldsRequired != null) {
      args.add(_fieldsRequired ? "-frequired" : "-foptional");
    }
    if (_searchDir != null) {
      for (final String searchDir : _searchDir.split("[;]")) {
        // hack to locate a relative directory
        String resolved = searchDir;
        if (resolved.startsWith("..{RELATIVE}../") || resolved.startsWith("..{RELATIVE}..\\")) {
          resolved = resolved.substring(15);
          File base = new File(_sourceDir).getParentFile();
          while (base != null) {
            File possible = new File(base, resolved);
            if (possible.exists()) {
              resolved = possible.getPath();
              break;
            }
            base = base.getParentFile();
          }
        }
        try {
          if (new File(resolved).getCanonicalPath().equals(new File(_sourceDir).getCanonicalPath())) {
            continue;  // try next searchDir
          }
        } catch (IOException ex) {
          throw new MojoExecutionException("Unable to resolve search directory: " + searchDir, ex);
        }
        if (new File(resolved).exists() == false) {
          throw new MojoExecutionException("Unable to find search directory: " + searchDir);
        }
        if (_verbose) {
          System.out.println("Searching " + resolved);
        }
        args.add("-p" + resolved);
      }
    }
    if (_equals)
      args.add("-Xequals");
    if (_toString)
      args.add("-XtoString");
    if (_hashCode)
      args.add("-XhashCode");
    if (_fudgeContext != null)
      args.add("-XfudgeContext=" + _fudgeContext);
    if (_gitIgnore)
      args.add("-XgitIgnore");
    if (_fileHeader != null) {
      args.add("-XfileHeader=" + _fileHeader);
    }
    if (_fileFooter != null) {
      args.add("-XfileFooter=" + _fileFooter);
    }
    if (_verbose) {
      if (_listFiles) {
        args.add("-vvv");
      } else {
        args.add("-v");
      }
    } else if (_listFiles) {
      args.add("-vv");
    }
    addFiles(new File(_sourceDir), args);
    if (_verbose) {
      System.out.print("Commandline:");
      for (final String arg : args) {
        System.out.print(' ');
        System.out.print(arg);
      }
      System.out.println();
    }

    // run generator
    getLog().info("Fudge-Proto generator started, directory: " + _sourceDir);
    try {
      if (CommandLine.compile(args.toArray(new String[args.size()])) > 0) {
        throw new MojoFailureException("Compilation failed");
      }
    } catch (MojoFailureException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new MojoFailureException("Error while running Fudge-Proto generator: " + ex.getMessage(), ex);
    }
    getLog().info("Fudge-Proto generator completed");
  }

  private int addFiles(File dir, List<String> names) {
    if (dir.exists() == false) {
      return 0;
    }
    int count = 0;
  fileloop:
    for (File file : dir.listFiles()) {
      final String name = file.getName();
      if (file.isDirectory()) {
        count += addFiles(file, names);
      } else {
        final int i = name.lastIndexOf('.');
        if (i >= 0) {
          if (".proto".equals(name.substring(i))) {
            if (!_rebuildAll) {
              String targetName = name.substring(0, name.length() - 6) + ".java";
              final File target = new File(file.getParentFile(), targetName);
              if (target.exists()) {
                if (target.lastModified() > file.lastModified()) {
                  if (_verbose && _listFiles) {
                    System.out.println("Ignoring " + file);
                  }
                  continue fileloop;
                }
              }
            }
            String path = file.getAbsolutePath();
            if (path.startsWith(_sourceDir)) {
              path = path.substring(_sourceDir.length());
              if (path.startsWith("/") || path.startsWith("\\")) {
                path = path.substring(1);
              }
            }
            if (_excludes != null) {
              final String[] excludes = _excludes.split("[;]");
              for (final String exclude : excludes) {
                if (DirectoryScanner.match(exclude, path)) {
                  if (_verbose && _listFiles) {
                    System.out.println("Excluding " + file);
                  }
                  continue fileloop;
                }
              }
            }
            if (_verbose && _listFiles) {
              System.out.println("Found " + file);
            }
            names.add(path);
            count++;
          }
        }
      }
    }
    return count;
  }

}
