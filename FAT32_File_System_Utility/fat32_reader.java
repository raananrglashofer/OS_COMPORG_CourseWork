import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class fat32_reader {

    private static Stack<Long> pathStack = new Stack<>(); // To handle navigation, especially for ".."
    private static String currentPath = "/";
    private static long currentCluster = 2;
    private static Field BPB_BytesPerSec = new Field("BPB_BytesPerSec", 0x0B, 2);
    private static Field BPB_SecPerClus = new Field("BPB_SecPerClus", 0x0D, 1);
    private static Field BPB_RsvdSecCnt = new Field("BPB_RsvdSecCnt", 0x0E, 2);
    private static Field BPB_NumFATS = new Field("BPB_NumFATS", 0x10, 1);
    private static Field BPB_FATSz32 = new Field("BPB_FATSz32", 0x24, 4);
    protected static Field BPB_RootClus = new Field("BPB_RootClus", 0x2C, 4);


    public static class Field {
        private String name;
        private int offset;
        private int size;

        public Field(String name, int offset, int size) {
            this.name = name;
            this.offset = offset;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public int getOffset() {
            return offset;
        }

        public int getSize() {
            return size;
        }

        @Override
        public String toString() {
            return name + ": Offset=" + offset + ", Size=" + size;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java fat32_reader <FAT32 image file>");
            return;
        }

        String filePath = args[0];
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r");
             Scanner scanner = new Scanner(System.in)) {

            ByteBuffer bootSector = ByteBuffer.allocate(512);
            file.readFully(bootSector.array());
            bootSector.order(ByteOrder.LITTLE_ENDIAN);

            String command;
            while (true) {
                System.out.print(currentPath + "] ");
                command = scanner.nextLine();
                processCommand(command, bootSector, file, scanner);
            }

        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        }
    }


    private static void processCommand(String commandLine, ByteBuffer bootSector, RandomAccessFile file, Scanner scanner) {
        String[] parts = commandLine.split(" ", 2); // Splits the command from the arguments
        String command = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";
        switch (command) {
            case "stop":
                stop(file, scanner);
                break;
            case "info":
                info(bootSector);
                break;
            case "ls":
                ls(file, bootSector);
                break;
            case "stat":
                stat(file, bootSector, argument);
                break;
            case "size":
                size(file, bootSector, argument);
                break;
            case "cd":
                cd(file, bootSector, argument);
                break;
            case "read":
                String[] readParts = argument.split(" ");
                String readFileName = readParts[0];
                int readOffset = Integer.parseInt(readParts[1]);
                int readNumBytes = Integer.parseInt(readParts[2]);
                read(file, bootSector, readFileName, readOffset, readNumBytes);
                break;
            default:
                System.out.println("Unknown command");
        }
    }

    public static void stop(RandomAccessFile file, Scanner scanner) {
        try {
            //System.out.println("Stopping the FAT32 utility"); // is this correct?
            if (file != null) {
                file.close(); // Ensure the file is closed properly
            }
            if (scanner != null) {
                scanner.close(); // Close the scanner
            }
            System.exit(0); // Cleanly exit the program
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
            System.exit(1); // Exit with error code if there is an issue closing the file
        }
    }


    public static void info(ByteBuffer bootSector) {
        System.out.println(getFieldInfo(bootSector, BPB_BytesPerSec));
        System.out.println(getFieldInfo(bootSector, BPB_SecPerClus));
        System.out.println(getFieldInfo(bootSector, BPB_RsvdSecCnt));
        System.out.println(getFieldInfo(bootSector, BPB_NumFATS));
        System.out.println(getFieldInfo(bootSector, BPB_FATSz32));
    }

    private static String getFieldInfo(ByteBuffer bootSector, Field field) {
        int value = getFieldValue(bootSector, field);
        return String.format("%s is 0x%x, %d", field.getName(), value, value);
    }

    private static int getFieldValue(ByteBuffer bootSector, Field field) {
        bootSector.position(field.getOffset());
        if (field.getSize() == 1) {
            return bootSector.get() & 0xFF;  // Unsigned byte
        } else if (field.getSize() == 2) {
            return bootSector.getShort() & 0xFFFF;  // Unsigned short
        } else if (field.getSize() == 4) {
            return bootSector.getInt();  // Integer
        }
        return -1;  // Error case, handle appropriately
    }

    public static void ls(RandomAccessFile file, ByteBuffer bootSector) {
        try {
            int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
            int sectorsPerCluster = getFieldValue(bootSector, BPB_SecPerClus);
            int reservedSectors = getFieldValue(bootSector, BPB_RsvdSecCnt);
            int numberOfFats = getFieldValue(bootSector, BPB_NumFATS);
            int fatSize = getFieldValue(bootSector, BPB_FATSz32);

            long rootDirSect = reservedSectors + (numberOfFats * fatSize) + (currentCluster - 2) * sectorsPerCluster;
            file.seek(rootDirSect * bytesPerSector);

            byte[] dirEntry = new byte[32];
            List<String> entries = new ArrayList<>();

            // Add entry for current directory
            entries.add(".");
            // Add entry for parent directory
            entries.add("..");

            while (file.read(dirEntry) == 32) {
                if (dirEntry[0] == 0x00) break;  // End of directory
                if (dirEntry[0] == 0xE5) continue;  // Skip deleted entries

                String extractedName = extractName(dirEntry);
                if (!extractedName.isEmpty() && !entries.contains(extractedName)) {
                    entries.add(extractedName);
                }
            }

            Collections.sort(entries);
            for (String entry : entries) {
                System.out.print(entry + " ");
            }
            System.out.println();
        } catch (IOException e) {
            System.out.println("Error reading directory: " + e.getMessage());
        }
    }



    private static String extractName(byte[] entry) {
        int attr = entry[11] & 0xFF; // Extract the attribute byte

        // Skip entries with the volume attribute
        if ((attr & 0x08) != 0) {
            return "";
        }

        // Skip entries based on specific attributes: system, hidden, LFN, or deleted entries
        if ((attr & 0x02) != 0 || (attr & 0x04) != 0 || (attr == 0x0F) || (entry[0] == 0xE5) || (entry[0] == 0x00)) {
            return "";
        }

        // Extract base name and extension, ensuring they are printable characters
        String rawName = new String(entry, 0, 8, StandardCharsets.US_ASCII).trim();
        String rawExt = new String(entry, 8, 3, StandardCharsets.US_ASCII).trim();

        // Check for non-printable characters and filter out names accordingly
        if (rawName.matches(".*[^\\x20-\\x7E]+.*")) {
            return "";
        }

        StringBuilder fullName = new StringBuilder(rawName);
        if (!rawExt.isEmpty() && !rawExt.matches(".*[^\\x20-\\x7E]+.*")) {
            fullName.append(".").append(rawExt);
        }

        return fullName.toString().toUpperCase();
    }


    public static void stat(RandomAccessFile file, ByteBuffer bootSector, String name) {
        try {
            int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
            int sectorsPerCluster = getFieldValue(bootSector, BPB_SecPerClus);
            int reservedSectors = getFieldValue(bootSector, BPB_RsvdSecCnt);
            int numberOfFats = getFieldValue(bootSector, BPB_NumFATS);
            int fatSize = getFieldValue(bootSector, BPB_FATSz32);

            // Compute the first sector of the root directory
            int rootDirSector = reservedSectors + (numberOfFats * fatSize);
            long rootDirOffset = rootDirSector * bytesPerSector;

            file.seek(rootDirOffset);
            byte[] entry = new byte[32];
            boolean found = false;

            while (file.read(entry) == 32) {
                if (entry[0] == 0x00) break; // End of directory entries
                if (entry[0] == 0xE5) continue; // Skip deleted entries

                String entryName = extractName(entry);
                if (entryName.equalsIgnoreCase(name)) {
                    found = true;
                    long firstClusterHigh = parseBytesToNumeric(entry, 20, 2);
                    //int firstClusterHigh = ByteBuffer.wrap(entry, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
                    long firstClusterLow = parseBytesToNumeric(entry, 26, 2);
                    //int firstClusterLow = ByteBuffer.wrap(entry, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
                    long nextCluster = (firstClusterHigh << 16) + firstClusterLow;


                    //int nextCluster = getNextClusterNumber(file, bootSector, firstCluster);
                    displayStatInfo(nextCluster, entry);
                    break;
                }
            }

            if (!found) {
                System.out.println("Error: file/directory does not exist");
            }
        } catch (IOException e) {
            System.out.println("Error accessing file: " + e.getMessage());
        }
    }

    private static String printHex (long val, int padding) {
        return String.format("0x%0"+padding+"X", val);
    }

    private static long parseBytesToNumeric(byte[] b, int offset, int len) {
        if (len > 7) System.out.println("WARNING. Attempted parseBytesToNumeric(byte[] b) for bytes=" + len + ". Returned signed long may overflow.");
        long ret = 0;
        for (int place = 0; place < len; place++)
            ret += (b[place + offset] & 0xFF)*Math.pow(256,place); //Properly read byte: unsigned_value * 256^place_in_endian_order. Use `& 0xFF` to force Java to treat as raw bits and then cast to larger data type
        return ret;
    }

    private static void displayStatInfo(long nextCluster, byte[] entry) {
        try {
            long size = ByteBuffer.wrap(entry, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            String attributes = getAttributes(entry[11]);

            System.out.println("Size is " + size);
            System.out.println("Attributes " + attributes);
            System.out.println("Next cluster number is " + printHex(nextCluster, 8));
        } catch (Exception e) {
            System.out.println("Error displaying file information: " + e.getMessage());
        }
    }


    public static long getNextClusterNumber(RandomAccessFile file, ByteBuffer bootSector, long currentCluster) throws IOException {
        int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
        int fatOffset = getFieldValue(bootSector, BPB_RsvdSecCnt) * bytesPerSector;
        int fatEntrySize = 4; // Each entry in FAT32 is 4 bytes
        long fatEntryPosition = fatOffset + (long) currentCluster * fatEntrySize;

        file.seek(fatEntryPosition);
        long nextCluster = file.readInt() & 0x0FFFFFFF; // Mask to ignore the high 4 bits

        // Check if the cluster is an end-of-chain marker
        if (nextCluster >= 0x0FFFFFF8) {
            return -1; // Signal to stop reading more clusters
        }
        return nextCluster;
    }



    private static String getAttributes(byte attr) {
        StringBuilder attrDesc = new StringBuilder();
        if ((attr & 0x01) != 0) attrDesc.append("ATTR_READ_ONLY ");
        if ((attr & 0x02) != 0) attrDesc.append("ATTR_HIDDEN ");
        if ((attr & 0x04) != 0) attrDesc.append("ATTR_SYSTEM ");
        if ((attr & 0x10) != 0) attrDesc.append("ATTR_DIRECTORY ");
        if ((attr & 0x20) != 0) attrDesc.append("ATTR_ARCHIVE ");
        if(attrDesc.length() == 0){
            return "NONE";
        }
        return attrDesc.toString().trim();
    }

    public static void size(RandomAccessFile file, ByteBuffer bootSector, String fileName) {
        try {
            int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
            int sectorsPerCluster = getFieldValue(bootSector, BPB_SecPerClus);
            int reservedSectors = getFieldValue(bootSector, BPB_RsvdSecCnt);
            int numberOfFats = getFieldValue(bootSector, BPB_NumFATS);
            int fatSize = getFieldValue(bootSector, BPB_FATSz32);


            // Compute the first sector of the root directory
            long rootDirOffset = (reservedSectors + (numberOfFats * fatSize)) * bytesPerSector;

            file.seek(rootDirOffset);
            byte[] entry = new byte[32];
            boolean found = false;

            while (file.read(entry) == 32) {

                if (entry[0] == 0x00) {
                    break; // End of directory entries
                }
                if (entry[0] == 0xE5) {
                    continue; // Skip deleted entries
                }

                String entryName = extractName(entry);

                if (entryName.equalsIgnoreCase(fileName)) {
                    found = true;
                    if ((entry[11] & 0x10) == 0x10) {
                        System.out.println("Error: " + fileName + " is not a file");
                    } else {
                        int size = ByteBuffer.wrap(entry, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        System.out.println("Size of " + fileName + " is " + size + " bytes");
                    }
                    break;
                }
            }

            if (!found) {
                System.out.println("Error: " + fileName + " is not a file");
            }

        } catch (IOException e) {
            System.out.println("Error reading directory: " + e.getMessage());
        }
    }

    public static void cd(RandomAccessFile file, ByteBuffer bootSector, String dirName) {
        try {
            if(dirName.isEmpty()){
                return;
            }
            dirName = dirName.toUpperCase();
            if (dirName.equals(".")) {
                // Stay in the current directory
                System.out.println(currentPath + "]");
                return;
            }

            if (dirName.equals("..")) {
                // Navigate to the parent directory
                if (!pathStack.isEmpty()) {
                    currentCluster = pathStack.pop();
                    // Update the path to reflect the change
                    int lastSlash = currentPath.lastIndexOf('/');
                    currentPath = lastSlash > 0 ? currentPath.substring(0, lastSlash) : "/";
                }
                return;
            }

            // Get directory information
            int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
            int sectorsPerCluster = getFieldValue(bootSector, BPB_SecPerClus);
            int reservedSectors = getFieldValue(bootSector, BPB_RsvdSecCnt);
            int numberOfFats = getFieldValue(bootSector, BPB_NumFATS);
            int fatSize = getFieldValue(bootSector, BPB_FATSz32);

            long sector = reservedSectors + (numberOfFats * fatSize) + (currentCluster - 2) * sectorsPerCluster;
            long offset = sector * bytesPerSector;
            file.seek(offset);

            byte[] entry = new byte[32];
            boolean found = false;


            while (file.read(entry) == 32) {
                if (entry[0] == 0x00) break;  // End of directory entries
                if (entry[0] == 0xE5) continue;  // Skip deleted entries

                String entryName = extractName(entry);
                if (entryName.equalsIgnoreCase(dirName)) {
                    if ((entry[11] & 0x10) != 0x10) {
                        System.out.println("Error: " + dirName + " is not a directory");
                        return;
                    }
                    pathStack.push(currentCluster); // Save current cluster before changing
                    long firstClusterHigh = parseBytesToNumeric(entry, 20, 2);
                    long firstClusterLow = parseBytesToNumeric(entry, 26, 2);
                    currentCluster = (firstClusterHigh << 16) + firstClusterLow;
                    found = true;
                    // Update the path to reflect the change
                    currentPath += (currentPath.endsWith("/") ? "" : "/") + dirName;
                    break;
                }
            }

            if (found) {
                return;
            } else {
                System.out.println("Error: " + dirName + " is not a directory");
            }
        } catch (IOException e) {
            System.out.println("Error reading directory: " + e.getMessage());
        }
    }


    public static void read(RandomAccessFile file, ByteBuffer bootSector, String fileName, int offset, int numBytes) {
        if(offset < 0){
            System.out.println("Error: OFFSET must be a positive value");
            return;
        }
        if(numBytes < 1){
            System.out.println("Error: NUM_BYTES must be greater than zero");
            return;
        }
        try {

            int bytesPerSector = getFieldValue(bootSector, BPB_BytesPerSec);
            int sectorsPerCluster = getFieldValue(bootSector, BPB_SecPerClus);
            int reservedSectors = getFieldValue(bootSector, BPB_RsvdSecCnt);
            int numberOfFats = getFieldValue(bootSector, BPB_NumFATS);
            int fatSize = getFieldValue(bootSector, BPB_FATSz32);

            // Compute the first sector of the data region
            int firstDataSector = reservedSectors + (numberOfFats * fatSize);
            long dataRegionOffset = firstDataSector * bytesPerSector;

            // Assuming the file is in the root directory for simplicity
            file.seek(dataRegionOffset);
            byte[] entry = new byte[32];
            boolean found = false;

            while (file.read(entry) == 32) {
                if (entry[0] == 0x00) break; // No more entries
                if (entry[0] == 0xE5) continue; // Deleted entry

                String entryName = extractName(entry);
                if (entryName.equalsIgnoreCase(fileName)) {
                    if ((entry[11] & 0x10) == 0x10) {
                        System.out.println("Error: " + fileName + " is not a file");
                        return;
                    }
                    long fileSize = parseBytesToNumeric(entry, 28, 4);
                    if (offset + numBytes > fileSize) {
                        System.out.println("Error: attempt to read data outside of file bounds");
                        return;
                    }

                    long firstClusterHigh = parseBytesToNumeric(entry, 20, 2);
                    long firstClusterLow = parseBytesToNumeric(entry, 26, 2);
                    long firstCluster = (firstClusterHigh << 16) + firstClusterLow;

                    // Calculate the actual offset in the file data to start reading
                    long clusterOffset = dataRegionOffset + (firstCluster - 2) * sectorsPerCluster * bytesPerSector + offset;
                    file.seek(clusterOffset);

                    byte[] data = new byte[numBytes];
                    file.readFully(data);

                    // Convert to ASCII string and print
                    System.out.println(new String(data, StandardCharsets.US_ASCII));
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println("Error: " + fileName +  " is not a file");
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

}