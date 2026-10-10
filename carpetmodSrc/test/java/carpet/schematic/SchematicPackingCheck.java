package carpet.schematic;

public final class SchematicPackingCheck {
    public static void main(String[] args) {
        checkBounds(new int[] {2, 4, 6, 4, 5, 9}, 2, 4, 6, 3, 2, 4, 24);
        checkBounds(new int[] {-5, 10, -9, -7, 8, -12}, -7, 8, -12, 3, 3, 4, 36);
        try {
            new SchematicSaveHandler.Bounds(new int[] {Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0});
            throw new AssertionError("Expected coordinate overflow rejection");
        } catch (ArithmeticException expected) {
        }
        checkPasteSequences();
        checkSignText();
        for (int bits = 2; bits <= 16; bits++) {
            int limit = 1 << bits;
            int[] values = new int[257];
            for (int i = 0; i < values.length; i++) values[i] = (i * 73 + 19) % limit;
            long[] packed = SchematicSaveHandler.packStates(values, bits);
            for (int i = 0; i < values.length; i++) {
                if (SchematicSaveHandler.unpackState(packed, i, bits) != values[i]) {
                    throw new AssertionError("Packing mismatch at " + i + " with " + bits + " bits");
                }
            }
        }
    }

    private static void checkSignText() {
        SchematicPasteHandler.validateSignText("{\"text\":\"Saved sign\",\"color\":\"gold\",\"extra\":[{\"text\":\" works\"}]}");
        SchematicPasteHandler.validateSignText("\"\"");
        String[] rejected = {
                "{\"text\":\"unsafe\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op someone\"}}",
                "{\"text\":\"unsafe\",\"extra\":[{\"text\":\"nested\",\"clickEvent\":{\"a\\u0063tion\":\"run_command\",\"value\":\"/op someone\"}}]}",
                "null", "{", "{\"text\":\"one\"}{\"text\":\"two\"}",
                new String(new char[34]).replace("\0", "[") + "\"deep\"" + new String(new char[34]).replace("\0", "]")
        };
        for (String text : rejected) {
            try {
                SchematicPasteHandler.validateSignText(text);
                throw new AssertionError("Unsafe or malformed sign text was accepted: " + text);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    private static void checkPasteSequences() {
        SchematicPasteHandler.Quota quota = new SchematicPasteHandler.Quota(7, 0);
        if (!quota.acceptSequence(0)) throw new AssertionError("Initial paste sequence was rejected");
        quota.recordAccepted(1);
        if (quota.acceptSequence(0)) throw new AssertionError("Duplicate paste sequence was accepted");
        try {
            quota.acceptSequence(2);
            throw new AssertionError("Out-of-order paste sequence was accepted");
        } catch (IllegalArgumentException expected) {
        }

        quota = new SchematicPasteHandler.Quota(8, 0);
        for (int sequence = 0; sequence < 100000; sequence++) {
            if (!quota.acceptSequence(sequence)) throw new AssertionError("Expected paste sequence was rejected");
            quota.recordAccepted(1);
        }
        if (quota.nextSequence != 100000 || quota.lastRequestTick != 1) throw new AssertionError("Large same-tick paste was truncated");
        if (quota.acceptSequence(99999)) throw new AssertionError("Large paste replay was accepted");
    }

    private static void checkBounds(int[] corners, int minX, int minY, int minZ,
                                    int sizeX, int sizeY, int sizeZ, long volume) {
        SchematicSaveHandler.Bounds bounds = new SchematicSaveHandler.Bounds(corners);
        if (bounds.minX != minX || bounds.minY != minY || bounds.minZ != minZ ||
                bounds.sizeX != sizeX || bounds.sizeY != sizeY || bounds.sizeZ != sizeZ || bounds.volume != volume) {
            throw new AssertionError("Incorrect normalized bounds");
        }
    }
}
