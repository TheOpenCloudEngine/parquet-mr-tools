#!/bin/sh

rpmbuild -bb --clean --define "_topdir $(pwd)" SPECS/parquet-mr-tools.spec
