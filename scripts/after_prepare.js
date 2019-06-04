#!/usr/bin/env node

/**
 * This scripts copies .crt files from root of project to
 * @type {module:fs}
 */

const fs = require('fs')
const n_path = require('path')

const certExt = '.crt'
const projectRoot = process.cwd()
const certSource = n_path.join(projectRoot, 'resources', 'android', 'raw')
const certDest = n_path.join(projectRoot, 'platforms', 'android', 'res', 'raw')

/**
 * Should work like mkdir -p
 * @param path
 * @param mode
 * @param position
 * @param callback
 * @returns {boolean|*}
 */
async function mkdir_p (path, mode) {
  return new Promise((resolve, reject) => {

    function mkdir_p_internal (path, mode, position) {
      // Which permissions to create the directories with
      const normalizedCreationMode = mode || '0777'
      const normalizedPosition = position || 1
      const parts = n_path.normalize(path).split(n_path.sep)

      // stop condition
      if (normalizedPosition >= parts.length) {
        return resolve()
      }
      const directory = parts.slice(0, normalizedPosition + 1).join(n_path.sep)
      fs.stat(directory, err => {
        if (err && err.code === 'ENOENT') {
          // Create missing directory
          fs.mkdir(directory, normalizedCreationMode, mkdirError => {
            if (mkdirError) {
              // Unable to create directory
              throw new Error(JSON.stringify(mkdirError))
            } else {
              return mkdir_p_internal(path, normalizedCreationMode,
                normalizedPosition + 1)
            }
          })
        } else if (err) {
          // Error wasn't missing directory PANIC!
          throw new Error(JSON.stringify(err))
        } else {
          return mkdir_p_internal(path, normalizedCreationMode,
            normalizedPosition + 1)
        }
      })
    }

    mkdir_p_internal(path, mode, 1)
  })
}

/**
 * Checks for the existence of .crt files in [project root]/resources/raw
 */
async function checkForCerts () {
  return new Promise((resolve, reject) => {
    const path = certSource
    console.log('checking for certificates')
    fs.access(path, fs.constants.F_OK, (err) => {
      if (err) {
        // Unable to find certificates directory
        reject(false)
      } else {
        fs.readdir(path, (readDirError, files) => {
          if (readDirError) {
            // We don't really care that we can't read the directory, just checking for existence
            reject(false)
          } else {
            // Check that directory contains certificates
            aCrtFile = files.find(value => n_path.extname(value) === certExt)
            if (aCrtFile) {
              // we found a .crt file
              resolve(true)
            } else {
              // No .crt files found
              reject()
            }
          }
        })
      }
    })
  })
}

/**
 * PRECONDITION: source and target are FILES and both source and target exists.
 * Copies source to target, replaces target if it exists
 *
 * Note from node 8.5 fs.copyFile can be used.
 *
 * @param source
 * @param target
 * @returns {Promise<*>}
 */
async function copyFile (source, target) {
  return new Promise((resolve, reject) => {
    console.info(`Copying ${source} to ${target}`)

    const readStream = fs.createReadStream(source)
    const writeStream = fs.createWriteStream(target)

    function endWithError (err) {
      throw err;
    }

    readStream.on('error', endWithError)
    writeStream.on('error', endWithError)
    writeStream.on('end', resolve)

    readStream.pipe(writeStream)
  })
}

/**
 * Copies .crt files from [project root]/resources/raw to [project root]/platforms/android/res/raw
 */
async function copyCerts () {

  return new Promise((resolve, reject) => {
    console.log('copying certificates')

    fs.readdir(certSource, (err, files) => {
      if (err) {
        reject(err)
      } else {
        if (files) {
          const resolvedCopies = files.
            filter(value => n_path.extname(value) === certExt).
            map(async (file) => {
              const src = n_path.join(certSource, file)
              const dst = n_path.join(certDest, file)
              return await copyFile(src, dst)
            })
          resolve(resolvedCopies)
        } else {
          // For sanity, just means that certificates were deleted white process was underway.
          resolve()
        }
      }
    })
  })

}

module.exports = async function init () {
  try {
    if (await checkForCerts()) {
      // Create destination directory
      await mkdir_p(certDest)
      await copyCerts()
    } else {
      console.info(
        'unable to find any crt files, as they are optional skipping any additional operations')
    }
  } catch (e) {
    console.error('error', JSON.stringify(e))
  }
}
