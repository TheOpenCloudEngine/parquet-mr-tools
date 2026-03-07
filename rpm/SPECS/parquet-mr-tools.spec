Name:           parquet-mr-tools
Version:        1.17.0 
Release:        1%{?dist}
Summary:        Parquet File CLI (Powered By Parquet MR) 
BuildArch:	noarch

License:        Apache License 
URL:            https://github.com/DataDynamics/parquet-mr-tools 

Requires:       bash

%define _buildrootdir %(pwd)
BuildRoot: %{_buildrootdir}

%description
Parquet MR Tools (Powered By Parquet MR)

%install
rm -rf /opt/parquet-mr-tools
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/parquet-mr-tools/lib
mkdir -p %{buildroot}/opt/parquet-mr-tools/bin
cp %{_topdir}/SOURCES/parquet %{buildroot}/opt/parquet-mr-tools/bin/
cp %{_topdir}/SOURCES/*.jar %{buildroot}/opt/parquet-mr-tools/lib/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
/opt/parquet-mr-tools/
/opt/parquet-mr-tools/bin/*
/opt/parquet-mr-tools/lib/*.jar


