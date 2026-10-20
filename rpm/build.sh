#!/bin/sh

rpmbuild -bb --clean --define "_topdir $(pwd)" SPECS/parquet-avro-tools.spec
