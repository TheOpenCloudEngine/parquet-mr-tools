Name:           parquet-mr-tools
Version:        1.18.1
Release:        1%{?dist}
Summary:        Parquet File CLI (Powered By Parquet MR)
BuildArch:      noarch

Vendor:         The Open Cloud Engine
Packager:       KIM BYOUNG GON <fharenheit@gmail.com>
License:        Apache-2.0
URL:            https://parquet.apache.org/

Requires:       bash
Requires:       java-17-openjdk

%description
Parquet MR Tools (Powered By Parquet MR)

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/parquet-mr-tools/lib
mkdir -p %{buildroot}/opt/parquet-mr-tools/bin
mkdir -p %{buildroot}/usr/bin

install -m 755 %{_topdir}/SOURCES/parquet %{buildroot}/opt/parquet-mr-tools/bin/parquet
cp %{_topdir}/SOURCES/*.jar %{buildroot}/opt/parquet-mr-tools/lib/

ln -sf /opt/parquet-mr-tools/bin/parquet %{buildroot}/usr/bin/parquet

%files
%defattr(-,root,root,-)
/opt/parquet-mr-tools/
/usr/bin/parquet
