package com.citymemory.data.local.seed

import com.citymemory.data.local.entities.CityEntity
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.domain.model.PlaceCategory

/**
 * The MVP dataset: one city, 80 places.
 *
 * Replacing this with a real dataset means replacing this file and nothing else
 * — no screen, ViewModel or repository refers to any id defined here.
 *
 * Coordinates are hand-entered and approximate. They are accurate enough to drop
 * a maps app on the right spot, not survey-grade. `imageUrl` is null throughout:
 * the app ships with no INTERNET permission, so the UI renders a generated
 * category-tinted placeholder instead. The field exists for a future dataset
 * that bundles or downloads real imagery.
 */
object MumbaiSeed {

    const val CITY_ID = "mumbai"

    val city = CityEntity(
        id = CITY_ID,
        name = "Mumbai",
        country = "India",
    )

    private fun place(
        id: String,
        name: String,
        category: PlaceCategory,
        latitude: Double,
        longitude: Double,
        description: String,
    ) = PlaceEntity(
        id = id,
        cityId = CITY_ID,
        name = name,
        category = category.id,
        description = description,
        latitude = latitude,
        longitude = longitude,
        imageUrl = null,
        displayOrder = 0,
    )

    private val tourist = listOf(
        place(
            "gateway-of-india", "Gateway of India", PlaceCategory.TOURIST, 18.9220, 72.8347,
            "The basalt arch on the harbour that every arrival to Mumbai once passed through.",
        ),
        place(
            "marine-drive", "Marine Drive", PlaceCategory.TOURIST, 18.9430, 72.8238,
            "Three kilometres of curved seafront that becomes a string of lights after dark.",
        ),
        place(
            "cst", "Chhatrapati Shivaji Maharaj Terminus", PlaceCategory.TOURIST, 18.9398, 72.8355,
            "A Victorian Gothic railway station, still moving three million people a day.",
        ),
        place(
            "haji-ali", "Haji Ali Dargah", PlaceCategory.TOURIST, 18.9827, 72.8090,
            "A white marble shrine on an islet, reachable only when the tide allows.",
        ),
        place(
            "juhu-beach", "Juhu Beach", PlaceCategory.TOURIST, 19.0968, 72.8265,
            "Sunset crowds, bhelpuri carts and the loudest stretch of sand in the city.",
        ),
        place(
            "siddhivinayak", "Siddhivinayak Temple", PlaceCategory.TOURIST, 19.0169, 72.8302,
            "Mumbai's most visited temple, with queues that start before sunrise.",
        ),
        place(
            "elephanta-caves", "Elephanta Caves", PlaceCategory.TOURIST, 18.9633, 72.9315,
            "Rock-cut Shiva temples on an island an hour's ferry across the harbour.",
        ),
        place(
            "bandra-fort", "Bandra Fort", PlaceCategory.TOURIST, 19.0430, 72.8190,
            "A Portuguese watchtower ruin looking straight down the Sea Link.",
        ),
        place(
            "kala-ghoda", "Kala Ghoda", PlaceCategory.TOURIST, 18.9285, 72.8324,
            "The city's art quarter — galleries, bookshops and restored stone facades.",
        ),
        place(
            "girgaon-chowpatty", "Girgaon Chowpatty", PlaceCategory.TOURIST, 18.9548, 72.8155,
            "The beach at the north end of Marine Drive, at its best during Ganesh visarjan.",
        ),
        place(
            "worli-sea-face", "Worli Sea Face", PlaceCategory.TOURIST, 19.0000, 72.8150,
            "A quieter promenade where the Sea Link fills the whole horizon.",
        ),
        place(
            "bandstand", "Bandstand Promenade", PlaceCategory.TOURIST, 19.0480, 72.8190,
            "Rocks, sea spray and the walk everyone in Bandra takes at dusk.",
        ),
        place(
            "mount-mary", "Mount Mary Basilica", PlaceCategory.TOURIST, 19.0448, 72.8244,
            "A hilltop church above Bandra with a view back over the bay.",
        ),
        place(
            "mahalaxmi-temple", "Mahalaxmi Temple", PlaceCategory.TOURIST, 18.9779, 72.8090,
            "A seaside temple to the goddess of fortune, just north of Haji Ali.",
        ),
        place(
            "global-vipassana-pagoda", "Global Vipassana Pagoda", PlaceCategory.TOURIST, 19.2340, 72.8100,
            "A vast golden dome at Gorai, built without a single supporting pillar.",
        ),
        place(
            "flora-fountain", "Flora Fountain", PlaceCategory.TOURIST, 18.9322, 72.8317,
            "The Portland stone fountain at the centre of the Fort district's crossroads.",
        ),
        place(
            "rajabai-clock-tower", "Rajabai Clock Tower", PlaceCategory.TOURIST, 18.9295, 72.8305,
            "A 85-metre campanile inside the university, modelled on Big Ben.",
        ),
        place(
            "colaba-causeway", "Colaba Causeway", PlaceCategory.TOURIST, 18.9150, 72.8258,
            "A street market that sells everything, negotiated at volume.",
        ),
        place(
            "versova-beach", "Versova Beach", PlaceCategory.TOURIST, 19.1350, 72.8120,
            "A working fishing beach at the northern end of the western shoreline.",
        ),
        place(
            "powai-lake", "Powai Lake", PlaceCategory.TOURIST, 19.1270, 72.9050,
            "An artificial lake ringed by hills, glass towers and the occasional crocodile.",
        ),
    )

    private val cafes = listOf(
        place(
            "leopold-cafe", "Leopold Cafe", PlaceCategory.CAFE, 18.9224, 72.8317,
            "Open since 1871, and unbothered about it.",
        ),
        place(
            "cafe-mondegar", "Cafe Mondegar", PlaceCategory.CAFE, 18.9218, 72.8320,
            "Mario Miranda murals, a jukebox, and Colaba's most reliable afternoon.",
        ),
        place(
            "kala-ghoda-cafe", "Kala Ghoda Cafe", PlaceCategory.CAFE, 18.9287, 72.8322,
            "A narrow courtyard cafe that started Mumbai's third-wave coffee habit.",
        ),
        place(
            "prithvi-cafe", "Prithvi Cafe", PlaceCategory.CAFE, 19.1090, 72.8270,
            "Irani chai under a banyan tree, between shows at the theatre next door.",
        ),
        place(
            "yazdani-bakery", "Yazdani Bakery", PlaceCategory.CAFE, 18.9332, 72.8340,
            "Brun maska and chai in a wooden-benched Irani bakery in Fort.",
        ),
        place(
            "kyani-and-co", "Kyani & Co.", PlaceCategory.CAFE, 18.9470, 72.8250,
            "One of the last true Irani cafes, running since 1904.",
        ),
        place(
            "cafe-madras", "Cafe Madras", PlaceCategory.CAFE, 19.0250, 72.8560,
            "Matunga's South Indian institution. Come early, expect a queue.",
        ),
        place(
            "blue-tokai-bandra", "Blue Tokai Coffee Roasters", PlaceCategory.CAFE, 19.0600, 72.8300,
            "Single-estate Indian coffee, roasted in-house.",
        ),
        place(
            "subko-bandra", "Subko Coffee", PlaceCategory.CAFE, 19.0620, 72.8290,
            "A bakehouse and roastery in a concrete shell off Pali Hill.",
        ),
        place(
            "koinonia", "Koinonia Coffee Roasters", PlaceCategory.CAFE, 19.0700, 72.8320,
            "Small, serious, and where the city's baristas learned to compete.",
        ),
        place(
            "cafe-zoe", "Cafe Zoe", PlaceCategory.CAFE, 18.9950, 72.8250,
            "A converted mill floor in Lower Parel with very high ceilings.",
        ),
        place(
            "the-nutcracker", "The Nutcracker", PlaceCategory.CAFE, 18.9290, 72.8320,
            "All-day breakfast in Kala Ghoda, reliably crowded by ten.",
        ),
        place(
            "grandmamas-cafe", "Grandmama's Cafe", PlaceCategory.CAFE, 19.0560, 72.8330,
            "Pastel walls and comfort food, a block off Linking Road.",
        ),
        place(
            "doolally-taproom", "Doolally Taproom", PlaceCategory.CAFE, 19.0620, 72.8340,
            "Board games, craft beer and no hurry whatsoever.",
        ),
    )

    private val restaurants = listOf(
        place(
            "britannia-and-co", "Britannia & Co.", PlaceCategory.RESTAURANT, 18.9340, 72.8390,
            "Parsi berry pulao, served in a room that has not changed since 1923.",
        ),
        place(
            "trishna", "Trishna", PlaceCategory.RESTAURANT, 18.9282, 72.8320,
            "The butter garlic crab that every other Mumbai seafood menu is chasing.",
        ),
        place(
            "bademiya", "Bademiya", PlaceCategory.RESTAURANT, 18.9210, 72.8310,
            "A late-night seekh kebab stall behind the Taj, best eaten standing up.",
        ),
        place(
            "mahesh-lunch-home", "Mahesh Lunch Home", PlaceCategory.RESTAURANT, 18.9290, 72.8330,
            "Mangalorean coastal cooking, and the gassi that made it famous.",
        ),
        place(
            "swati-snacks", "Swati Snacks", PlaceCategory.RESTAURANT, 18.9700, 72.8110,
            "Gujarati street food done precisely, at steel tables.",
        ),
        place(
            "shree-thaker-bhojanalay", "Shree Thaker Bhojanalay", PlaceCategory.RESTAURANT, 18.9520, 72.8300,
            "An unlimited Gujarati thali in Kalbadevi. Arrive hungry.",
        ),
        place(
            "the-bombay-canteen", "The Bombay Canteen", PlaceCategory.RESTAURANT, 18.9950, 72.8240,
            "Regional Indian cooking rebuilt as a modern menu.",
        ),
        place(
            "bastian-bandra", "Bastian", PlaceCategory.RESTAURANT, 19.0640, 72.8320,
            "Seafood and a room that Bandra spends its weekends trying to get into.",
        ),
        place(
            "gajalee", "Gajalee", PlaceCategory.RESTAURANT, 19.1150, 72.8340,
            "Malvani seafood — bombil fry, prawn koliwada, tandoori pomfret.",
        ),
        place(
            "olive-bar-kitchen", "Olive Bar & Kitchen", PlaceCategory.RESTAURANT, 19.0680, 72.8230,
            "A whitewashed Mediterranean courtyard tucked behind Pali Hill.",
        ),
        place(
            "noor-mohammadi", "Noor Mohammadi Hotel", PlaceCategory.RESTAURANT, 18.9600, 72.8280,
            "Bhendi Bazaar's century-old kitchen, and the original chicken sanju baba.",
        ),
        place(
            "ram-ashraya", "Ram Ashraya", PlaceCategory.RESTAURANT, 19.0270, 72.8560,
            "Matunga breakfasts since 1939 — the filter coffee is the point.",
        ),
        place(
            "aaswad", "Aaswad", PlaceCategory.RESTAURANT, 19.0210, 72.8420,
            "Maharashtrian home cooking in Dadar, and an award-winning misal pav.",
        ),
        place(
            "highway-gomantak", "Highway Gomantak", PlaceCategory.RESTAURANT, 19.0730, 72.8390,
            "Goan-Malvani fish thalis, unfussy and very good.",
        ),
    )

    private val parks = listOf(
        place(
            "sanjay-gandhi-np", "Sanjay Gandhi National Park", PlaceCategory.PARK, 19.2147, 72.9106,
            "A hundred square kilometres of forest inside the city limits, leopards included.",
        ),
        place(
            "hanging-gardens", "Hanging Gardens", PlaceCategory.PARK, 18.9567, 72.8050,
            "Terraced topiary on Malabar Hill, above the reservoir.",
        ),
        place(
            "kamala-nehru-park", "Kamala Nehru Park", PlaceCategory.PARK, 18.9560, 72.8043,
            "The boot-shaped playground with the classic view down over Chowpatty.",
        ),
        place(
            "shivaji-park", "Shivaji Park", PlaceCategory.PARK, 19.0280, 72.8370,
            "Dadar's great open maidan, and where Mumbai's cricket is actually learned.",
        ),
        place(
            "priyadarshini-park", "Priyadarshini Park", PlaceCategory.PARK, 18.9560, 72.7950,
            "A seaside sports park at the tip of Malabar Hill.",
        ),
        place(
            "maharashtra-nature-park", "Maharashtra Nature Park", PlaceCategory.PARK, 19.0480, 72.8560,
            "A forest grown on a former landfill beside the Mithi river.",
        ),
        place(
            "five-gardens", "Five Gardens", PlaceCategory.PARK, 19.0180, 72.8450,
            "Five squares of green in the Dadar Parsi Colony, quiet at any hour.",
        ),
        place(
            "joggers-park", "Joggers Park", PlaceCategory.PARK, 19.0570, 72.8230,
            "A small seafront loop in Bandra, busiest at 6am.",
        ),
        place(
            "horniman-circle-garden", "Horniman Circle Gardens", PlaceCategory.PARK, 18.9330, 72.8360,
            "A circular lawn ringed by colonnaded facades in the Fort district.",
        ),
        place(
            "sagar-upvan", "Sagar Upvan Garden", PlaceCategory.PARK, 18.9080, 72.8180,
            "A little-known coastal garden past Colaba, almost always empty.",
        ),
        place(
            "byculla-zoo", "Veermata Jijabai Bhosale Udyan", PlaceCategory.PARK, 18.9790, 72.8340,
            "The old Byculla botanical garden and zoo, founded 1862.",
        ),
    )

    private val culture = listOf(
        place(
            "csmvs", "Chhatrapati Shivaji Maharaj Vastu Sangrahalaya", PlaceCategory.CULTURE, 18.9269, 72.8328,
            "The city's principal museum, under an Indo-Saracenic dome.",
        ),
        place(
            "jehangir-art-gallery", "Jehangir Art Gallery", PlaceCategory.CULTURE, 18.9276, 72.8317,
            "Four galleries in Kala Ghoda that rotate every week.",
        ),
        place(
            "bhau-daji-lad", "Dr. Bhau Daji Lad Museum", PlaceCategory.CULTURE, 18.9788, 72.8342,
            "Mumbai's oldest museum, restored to its full Victorian colour.",
        ),
        place(
            "mani-bhavan", "Mani Bhavan Gandhi Museum", PlaceCategory.CULTURE, 18.9600, 72.8100,
            "The Gamdevi house Gandhi worked from for seventeen years.",
        ),
        place(
            "asiatic-society", "Asiatic Society Library", PlaceCategory.CULTURE, 18.9330, 72.8360,
            "Thirty neoclassical steps up to a two-hundred-year-old reading room.",
        ),
        place(
            "royal-opera-house", "Royal Opera House", PlaceCategory.CULTURE, 18.9560, 72.8180,
            "India's only surviving opera house, restored after decades dark.",
        ),
        place(
            "ncpa", "National Centre for the Performing Arts", PlaceCategory.CULTURE, 18.9230, 72.8200,
            "Five venues at the tip of Nariman Point, from chamber music to theatre.",
        ),
        place(
            "prithvi-theatre", "Prithvi Theatre", PlaceCategory.CULTURE, 19.1090, 72.8272,
            "A 200-seat Juhu theatre that has shaped Indian stage acting since 1978.",
        ),
        place(
            "kanheri-caves", "Kanheri Caves", PlaceCategory.CULTURE, 19.2080, 72.9060,
            "109 Buddhist caves cut into basalt inside the national park.",
        ),
        place(
            "nehru-science-centre", "Nehru Science Centre", PlaceCategory.CULTURE, 19.0000, 72.8280,
            "India's largest interactive science centre, at Worli.",
        ),
        place(
            "nehru-planetarium", "Nehru Planetarium", PlaceCategory.CULTURE, 18.9900, 72.8180,
            "A domed sky show at Worli that has barely aged since 1977.",
        ),
        place(
            "keneseth-eliyahoo", "Keneseth Eliyahoo Synagogue", PlaceCategory.CULTURE, 18.9280, 72.8320,
            "A sky-blue 1884 synagogue on a Kala Ghoda side street.",
        ),
    )

    private val hiddenGems = listOf(
        place(
            "banganga-tank", "Banganga Tank", PlaceCategory.HIDDEN_GEM, 18.9455, 72.7943,
            "A stepped water tank and temple village hidden inside Malabar Hill.",
        ),
        place(
            "khotachiwadi", "Khotachiwadi", PlaceCategory.HIDDEN_GEM, 18.9560, 72.8210,
            "A surviving lane of Portuguese-style wooden houses off Girgaon.",
        ),
        place(
            "sassoon-docks", "Sassoon Docks", PlaceCategory.HIDDEN_GEM, 18.9130, 72.8320,
            "The fishing dock at 5am — the loudest, most alive hour in Colaba.",
        ),
        place(
            "gilbert-hill", "Gilbert Hill", PlaceCategory.HIDDEN_GEM, 19.1350, 72.8360,
            "A 60-metre column of basalt in Andheri, 66 million years old.",
        ),
        place(
            "sewri-jetty", "Sewri Mudflats", PlaceCategory.HIDDEN_GEM, 18.9970, 72.8620,
            "Flamingos on the eastern mudflats, roughly November to March.",
        ),
        place(
            "worli-village", "Worli Village & Fort", PlaceCategory.HIDDEN_GEM, 19.0100, 72.8130,
            "A koliwada fishing village and small fort, under the Sea Link.",
        ),
        place(
            "afghan-church", "Afghan Church", PlaceCategory.HIDDEN_GEM, 18.9060, 72.8140,
            "A quiet Gothic church in Navy Nagar with remarkable stained glass.",
        ),
        place(
            "chor-bazaar", "Chor Bazaar", PlaceCategory.HIDDEN_GEM, 18.9600, 72.8300,
            "The 'thieves market' — antiques, salvage and negotiable provenance.",
        ),
        place(
            "dhobi-ghat", "Mahalaxmi Dhobi Ghat", PlaceCategory.HIDDEN_GEM, 18.9772, 72.8226,
            "The open-air laundry, best seen from the bridge above it.",
        ),
    )

    /**
     * Display order is assigned here rather than typed by hand so places can be
     * reordered or inserted without renumbering the whole file.
     */
    val places: List<PlaceEntity> =
        (tourist + cafes + restaurants + parks + culture + hiddenGems)
            .mapIndexed { index, place -> place.copy(displayOrder = index) }
}
