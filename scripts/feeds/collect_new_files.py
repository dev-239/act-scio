#!/usr/bin/env python3
"""Copyright 2019 mnemonic AS <opensource@mnemonic.no>

Permission to use, copy, modify, and/or distribute this software for
any purpose with or without fee is hereby granted, provided that the
above copyright notice and this permission notice appear in all
copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL
DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.

---
Program to check a directory against a database of cached content (based on hexdigest).
Any filenames _not_ in the cache is printed on standard out or saved to an output file.
"""

from datetime import datetime

import argparse
import hashlib
import os
import sys
import sqlite3
import magic


def init():
    """Initialize the argument parser"""

    parser = argparse.ArgumentParser(description="Get list of uncached files")
    parser.add_argument("-c", "--cache", type=str, help="SQLite database with cache.")
    parser.add_argument("-a", "--add", action="store_true", help="Store uncached files to cache.")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose error messages.")
    parser.add_argument("-o", "--output", type=str, help="Where to save filenames (default: stdout)")
    parser.add_argument("directories", metavar="DIR", type=str, nargs='+', help="Which directories to scan")

    return parser.parse_args()


def should_upload(mime, file_name):
    """Check if a file should be uploaded, based on file extension and  mime type"""

    always_upload = [".xml", ".csv", ".html"]

    if mime.from_file(file_name).startswith("application"):
        return True

    # check if the file is actually an upload candidate.
    for extension in always_upload:
        if extension in file_name:
            return True

    return False


def check_directories(args, cache, directories):
    """check a list of directories for cached content"""

    mime = magic.Magic(mime=True)

    for directory in directories:
        paths = [os.path.join(directory, x) for x in os.listdir(directory)]
        files = [path for path in paths if os.path.isfile(path)]
        subdir = [path for path in paths if os.path.isdir(path)]

        for file_name in files:
            try:
                sha256 = hashlib.sha256(open(file_name, "rb").read()).hexdigest()
            except IOError as err:
                if args.verbose:
                    sys.stderr.write("{0}\n".format(err))
                continue

            if not should_upload(mime, file_name):
                continue

            if not cache.contains(sha256):
                if not args.output:
                    print(file_name)
                else:
                    with open(args.output, 'a') as fp:
                        fp.write('{}\n'.format(file_name))

                if args.add:
                    cache.append(sha256)

        check_directories(args, cache, subdir)


class Cache(object):
    """Cache controller"""

    def __init__(self, cache_file_name):
        self.conn = sqlite3.connect(cache_file_name)

    def contains(self, sha256_digest):
        """inCach check if a sha256 is already in the cache"""

        sql = "SELECT id FROM files WHERE sha256 = ?"
        cur = self.conn.execute(sql, (sha256_digest,))

        if cur.fetchall():
            return True

        return False

    def append(self, sha256_digest):
        """append a sha256 to the cache"""

        sql = "INSERT INTO files(sha256, description) VALUES (?, ?)"
        self.conn.execute(sql, (sha256_digest, datetime.now().isoformat()))
        self.conn.commit()


def main(args):
    """Main program body"""

    cache = Cache(args.cache)
    check_directories(args, cache, args.directories)


if __name__ == '__main__':
    args = init()
    main(args)
