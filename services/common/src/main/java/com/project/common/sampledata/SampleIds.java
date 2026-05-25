package com.project.common.sampledata;

import java.util.List;
import java.util.UUID;

/**
 * Stable IDs used across sample-data seeders in every service. Sharing one
 * source of truth means orders can reference user / product IDs that
 * actually exist on the user and product services without each seeder
 * having to query the others first.
 *
 * <p>Format conventions:
 * <ul>
 *   <li>Users    — UUIDs in the {@code 11111111-...} family for customers,
 *       {@code 22222222-...} for sellers, {@code 33333333-...} for support.</li>
 *   <li>Products — string ids prefixed {@code prod-...} (Mongo _id is a String).</li>
 *   <li>Categories — string ids prefixed {@code cat-...}.</li>
 *   <li>Orders / payments / notifications — generated at runtime.</li>
 * </ul>
 *
 * <p>Lengths kept to 24-30 entries per type to match the request brief.
 */
public final class SampleIds {

    private SampleIds() {}

    // ---- Users (20 customers) ----------------------------------------------
    public static final List<SampleUser> CUSTOMERS = List.of(
            customer(1, "amelia.park@example.com",       "Amelia Park"),
            customer(2, "noah.iyer@example.com",         "Noah Iyer"),
            customer(3, "olivia.mehta@example.com",      "Olivia Mehta"),
            customer(4, "liam.fernandes@example.com",    "Liam Fernandes"),
            customer(5, "ava.banerjee@example.com",      "Ava Banerjee"),
            customer(6, "ethan.kapoor@example.com",      "Ethan Kapoor"),
            customer(7, "sophia.reddy@example.com",      "Sophia Reddy"),
            customer(8, "mateo.dsouza@example.com",      "Mateo D'Souza"),
            customer(9, "isabella.shah@example.com",     "Isabella Shah"),
            customer(10, "lucas.menon@example.com",      "Lucas Menon"),
            customer(11, "mia.verma@example.com",        "Mia Verma"),
            customer(12, "leo.kulkarni@example.com",     "Leo Kulkarni"),
            customer(13, "harper.singh@example.com",     "Harper Singh"),
            customer(14, "owen.bose@example.com",        "Owen Bose"),
            customer(15, "ella.chowdhury@example.com",   "Ella Chowdhury"),
            customer(16, "henry.patil@example.com",      "Henry Patil"),
            customer(17, "luna.rao@example.com",         "Luna Rao"),
            customer(18, "kai.sharma@example.com",       "Kai Sharma"),
            customer(19, "zara.bhatt@example.com",       "Zara Bhatt"),
            customer(20, "rowan.malhotra@example.com",   "Rowan Malhotra")
    );

    // ---- Users (5 sellers) -------------------------------------------------
    public static final List<SampleUser> SELLERS = List.of(
            seller(1, "heirloom@sellers.example.com",   "Heirloom Goods"),
            seller(2, "aurora@sellers.example.com",     "Aurora Atelier"),
            seller(3, "fold@sellers.example.com",       "Fold & Form"),
            seller(4, "kindling@sellers.example.com",   "Kindling Studio"),
            seller(5, "northrun@sellers.example.com",   "North Run Co.")
    );

    // ---- Users (1 support) -------------------------------------------------
    public static final SampleUser SUPPORT = new SampleUser(
            UUID.fromString("33333333-3333-3333-3333-000000000001"),
            "support@local.test",
            "Support Desk",
            "ROLE_SUPPORT");

    // ---- Categories (8) ----------------------------------------------------
    public static final List<SampleCategory> CATEGORIES = List.of(
            new SampleCategory("cat-apparel",     "Apparel",     "Considered everyday clothing."),
            new SampleCategory("cat-bags",        "Bags",        "Totes, backpacks, weekenders."),
            new SampleCategory("cat-home",        "Home",        "Things that make a room feel lived-in."),
            new SampleCategory("cat-kitchen",     "Kitchen",     "Tools that get better with use."),
            new SampleCategory("cat-stationery",  "Stationery",  "Paper, ink, leatherbound notebooks."),
            new SampleCategory("cat-tech",        "Tech",        "Quietly designed accessories."),
            new SampleCategory("cat-outdoor",     "Outdoor",     "Travel and the long walk."),
            new SampleCategory("cat-grooming",    "Grooming",    "Soaps, oils, and small ceremonies.")
    );

    // ---- Products (30 — 3-4 per category) ----------------------------------
    public static final List<SampleProduct> PRODUCTS = List.of(
            // Apparel
            product("prod-apparel-tee-oat",       "TEE-OAT",        "Heavyweight tee, oat",                "cat-apparel",    1499, "Brushed cotton, dropped shoulder.",                                                  0),
            product("prod-apparel-tee-ink",       "TEE-INK",        "Heavyweight tee, ink",                "cat-apparel",    1499, "Brushed cotton, dropped shoulder.",                                                  0),
            product("prod-apparel-shirt-linen",   "SHIRT-LIN",      "Linen overshirt, dust",               "cat-apparel",    3699, "Heavy slubbed linen. Camp collar.",                                                  0),
            product("prod-apparel-trouser-pleat", "TROUSER-PLT",    "Pleated wool trouser",                "cat-apparel",    5499, "Italian wool, pleated front, tapered leg.",                                          0),
            // Bags
            product("prod-bag-tote-linen-oat",    "TOTE-LIN-OAT",   "Linen tote, oat",                     "cat-bags",       1499, "Heavy oat linen. Cotton webbing handles.",                                           1),
            product("prod-bag-weekender",         "WEEKENDER",      "Weekender bag, leather",              "cat-bags",       8999, "Veg-tan leather, brass hardware.",                                                   1),
            product("prod-bag-backpack-canvas",   "BPACK-CANVAS",   "Roll-top canvas backpack",            "cat-bags",       4499, "Waxed canvas, leather straps, 25 L.",                                                1),
            product("prod-bag-pouch-leather",     "POUCH-LTR",      "Card pouch, chestnut",                "cat-bags",        899, "Vegetable-tanned leather, four slots.",                                              1),
            // Home
            product("prod-home-throw-merino",     "THROW-MER",      "Merino throw, undyed",                "cat-home",       6499, "Single-source Australian merino, hand-finished.",                                    2),
            product("prod-home-vase-stoneware",   "VASE-STONE",     "Stoneware vase, ash",                 "cat-home",       2299, "Wheel-thrown, matte ash glaze.",                                                     2),
            product("prod-home-candle-tobac",     "CNDL-TOBAC",     "Candle, tobacco & vetiver",           "cat-home",       1899, "Coconut-soy wax, 60 hours.",                                                         2),
            product("prod-home-mirror-brass",     "MIRROR-BRS",     "Wall mirror, brushed brass",          "cat-home",       3499, "Solid brass frame, 30 cm.",                                                          2),
            // Kitchen
            product("prod-kitchen-board-ash",     "BOARD-ASH",      "Ash cutting board",                   "cat-kitchen",    1899, "Single piece of European ash, oiled.",                                               3),
            product("prod-kitchen-knife-paring",  "KNIFE-PAR",      "Paring knife, takamura",              "cat-kitchen",    4299, "VG-10 core, walnut handle.",                                                         3),
            product("prod-kitchen-press-coffee",  "PRESS-COFFEE",   "Glass coffee press, 1L",              "cat-kitchen",    2199, "Heat-resistant glass, walnut collar.",                                               3),
            product("prod-kitchen-apron-canvas",  "APRON-CAN",      "Cross-back apron, sand",              "cat-kitchen",    1799, "Heavy canvas, leather buckle.",                                                      3),
            // Stationery
            product("prod-stat-notebook-leather", "NB-LTR",         "Leatherbound notebook, A5",           "cat-stationery", 1299, "Tomoe River paper, 192 pages.",                                                      4),
            product("prod-stat-pen-machined",     "PEN-MCH",        "Machined ballpoint pen, brass",       "cat-stationery", 1599, "Solid brass, takes Parker refills.",                                                 4),
            product("prod-stat-ink-rollerball",   "INK-RB",         "Rollerball ink set",                  "cat-stationery",  799, "Three colours: ash, walnut, ink.",                                                   4),
            product("prod-stat-folio-card",       "FOLIO-CRD",      "Card folio, oxblood",                 "cat-stationery", 1199, "Hand-stitched, fits 50 cards.",                                                      4),
            // Tech
            product("prod-tech-stand-laptop",     "STAND-LAP",      "Laptop stand, walnut",                "cat-tech",       4299, "Solid walnut, fits 13-16 inch.",                                                     5),
            product("prod-tech-cable-braid",      "CABLE-BR",       "Braided USB-C cable, 2m",             "cat-tech",        899, "Nylon braid, anodized aluminium ends.",                                              5),
            product("prod-tech-mat-desk",         "MAT-DESK",       "Leather desk mat, large",             "cat-tech",       2999, "Oak-tanned leather, 90×40 cm.",                                                      5),
            product("prod-tech-dock-walnut",      "DOCK-WAL",       "Walnut phone dock",                   "cat-tech",       1499, "Magsafe-compatible cradle.",                                                         5),
            // Outdoor
            product("prod-outdoor-bottle-steel",  "BOTTLE-ST",      "Insulated bottle, 750ml",             "cat-outdoor",    1899, "Double-wall steel, copper-lined.",                                                   6),
            product("prod-outdoor-blanket-wool",  "BLANK-WOOL",     "Picnic blanket, herringbone",         "cat-outdoor",    3499, "Recycled wool, water-resistant back.",                                               6),
            product("prod-outdoor-lantern-oil",   "LAMP-OIL",       "Brass oil lantern, small",            "cat-outdoor",    2499, "Burns 8 hours on a fill.",                                                           6),
            // Grooming
            product("prod-groom-soap-cedar",      "SOAP-CDR",       "Bar soap, cedar & lime",              "cat-grooming",    499, "Cold-process, 110 g.",                                                               7),
            product("prod-groom-oil-beard",       "OIL-BRD",        "Beard oil, juniper",                  "cat-grooming",    899, "Cold-pressed botanicals.",                                                           7),
            product("prod-groom-towel-linen",     "TOWEL-LIN",      "Linen face towel, sage",              "cat-grooming",    1199, "Pre-washed European linen.",                                                         7)
    );

    // ---- helpers -----------------------------------------------------------

    private static SampleUser customer(int n, String email, String name) {
        return new SampleUser(seqUuid(0x11111111, n), email, name, "ROLE_CUSTOMER");
    }

    private static SampleUser seller(int n, String email, String name) {
        return new SampleUser(seqUuid(0x22222222, n), email, name, "ROLE_SELLER");
    }

    private static UUID seqUuid(int prefix, int n) {
        long msb = ((long) prefix << 32) | (long) prefix;
        long lsb = ((long) prefix << 32) | (long) n;
        return new UUID(msb, lsb);
    }

    private static SampleProduct product(String id, String sku, String name, String categoryId,
                                         int priceRupees, String description, int sellerIndex) {
        return new SampleProduct(id, sku, name, categoryId, priceRupees, description, sellerIndex);
    }

    public record SampleUser(UUID id, String email, String displayName, String role) {}

    public record SampleCategory(String id, String name, String description) {}

    /**
     * @param sellerIndex 0..7, mapped to one of the 5 SELLERS via modulo.
     */
    public record SampleProduct(
            String id, String sku, String name, String categoryId,
            int priceRupees, String description, int sellerIndex) {

        public UUID sellerId() {
            return SELLERS.get(sellerIndex % SELLERS.size()).id();
        }
    }
}
