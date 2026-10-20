Name:           parquet-avro-tools
Version:        1.18.1
Release:        3%{?dist}
Summary:        Parquet File CLI (Powered By Parquet MR) and Avro Tools
BuildArch:      noarch

Vendor:         The Open Cloud Engine
Packager:       KIM BYOUNG GON <fharenheit@gmail.com>
License:        Apache-2.0
URL:            https://parquet.apache.org/

Requires:       bash
Requires:       java-17-openjdk
Recommends:     bash-completion

# Replaces the package previously named parquet-mr-tools
Provides:       parquet-mr-tools = %{version}-%{release}
Obsoletes:      parquet-mr-tools < %{version}-%{release}

%description
Parquet & Avro Tools: Parquet CLI (Powered By Parquet MR) with INT96 timestamp support and Apache Avro Tools

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/parquet-avro-tools/lib
mkdir -p %{buildroot}/opt/parquet-avro-tools/avro
mkdir -p %{buildroot}/opt/parquet-avro-tools/bin
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/bash-completion/completions

install -m 755 %{_topdir}/SOURCES/parquet %{buildroot}/opt/parquet-avro-tools/bin/parquet
install -m 755 %{_topdir}/SOURCES/avro %{buildroot}/opt/parquet-avro-tools/bin/avro
cp %{_topdir}/SOURCES/*.jar %{buildroot}/opt/parquet-avro-tools/lib/
cp %{_topdir}/SOURCES/avro-tools/*.jar %{buildroot}/opt/parquet-avro-tools/avro/

ln -sf /opt/parquet-avro-tools/bin/parquet %{buildroot}/usr/bin/parquet
ln -sf /opt/parquet-avro-tools/bin/avro %{buildroot}/usr/bin/avro

install -m 644 %{_topdir}/SOURCES/parquet.bash-completion %{buildroot}/usr/share/bash-completion/completions/parquet
install -m 644 %{_topdir}/SOURCES/avro.bash-completion %{buildroot}/usr/share/bash-completion/completions/avro

%files
%defattr(-,root,root,-)
/opt/parquet-avro-tools/
/usr/bin/parquet
/usr/bin/avro
/usr/share/bash-completion/completions/parquet
/usr/share/bash-completion/completions/avro
