import struct
import sys
import zipfile


def read_uleb128(data, off):
    result = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, off
        shift += 7


def defined_classes(data):
    if data[:4] != b'dex\n':
        raise ValueError('not a DEX file')
    (string_ids_size, string_ids_off) = struct.unpack_from('<II', data, 0x38)
    (type_ids_size, type_ids_off) = struct.unpack_from('<II', data, 0x40)
    (class_defs_size, class_defs_off) = struct.unpack_from('<II', data, 0x60)

    strings = []
    for i in range(string_ids_size):
        (so,) = struct.unpack_from('<I', data, string_ids_off + 4 * i)
        _, off = read_uleb128(data, so)
        end = data.index(b'\x00', off)
        strings.append(data[off:end])

    types = []
    for i in range(type_ids_size):
        (di,) = struct.unpack_from('<I', data, type_ids_off + 4 * i)
        types.append(strings[di])

    classes = []
    for i in range(class_defs_size):
        (class_idx,) = struct.unpack_from('<I', data, class_defs_off + 32 * i)
        classes.append(types[class_idx])
    return classes


def main(apk_path):
    bad = []
    with zipfile.ZipFile(apk_path) as z:
        dex_entries = [n for n in z.namelist()
                       if n == 'classes.dex' or n.startswith('classes') and n.endswith('.dex')]
        for name in dex_entries:
            for desc in defined_classes(z.read(name)):
                if desc.startswith(b'Lde/robv/android/xposed/'):
                    bad.append((name, desc.decode()))
    if bad:
        for name, desc in bad:
            print('XPOSED CLASS DEFINED IN %s: %s' % (name, desc))
        print('FAIL: Xposed API classes are packaged in the APK')
        return 1
    print('OK: no de.robv.android.xposed classes defined in any DEX')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
