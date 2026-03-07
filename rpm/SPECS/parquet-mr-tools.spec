Name:           parquet-mr-tools
Version:        1.17.0 
Release:        1%{?dist}
Summary:        Parquet File CLI (Powered By Parquet MR) 
BuildArch:	noarch

Vendor:         The Open Cloud Engine
Packager:       KIM BYOUNG GON <fharenheit@gmail.com>
License:        Apache License 
URL:            https://parquet.apache.org/ 

Requires:       bash
Requires:       java-17-openjdk

%define _buildrootdir %(pwd)
BuildRoot: %{_buildrootdir}

%description
Parquet MR Tools (Powered By Parquet MR)

%install
rm -rf /opt/parquet-mr-tools
rm -rf %{buildroot}

mkdir -p %{buildroot}/opt/parquet-mr-tools/lib
mkdir -p %{buildroot}/opt/parquet-mr-tools/bin
mkdir -p %{buildroot}/usr/bin

cp %{_topdir}/SOURCES/parquet %{buildroot}/opt/parquet-mr-tools/bin/
cp %{_topdir}/SOURCES/*.jar %{buildroot}/opt/parquet-mr-tools/lib/

install -m 755 %{_topdir}/SOURCES/parquet %{buildroot}/opt/parquet-mr-tools/bin/parquet

ln -sf /opt/parquet-mr-tools/bin/parquet %{buildroot}/usr/bin/parquet

%clean


%files
%defattr(-,root,root,-)
/opt/parquet-mr-tools/
/opt/parquet-mr-tools/bin/*
/opt/parquet-mr-tools/lib/*.jar
/usr/bin/parquet

