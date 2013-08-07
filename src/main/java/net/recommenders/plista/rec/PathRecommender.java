package net.recommenders.plista.rec;

import de.dailab.plistacontest.recommender.ContestItem;
import de.dailab.plistacontest.recommender.ContestRecommender;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.log4j.Logger;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.recommenders.plista.utils.JsonUtils;

/**
 *
 * @author alejandr
 */
public class PathRecommender implements ContestRecommender {

    private static final Logger logger = Logger.getLogger(PathRecommender.class);
    private Map<Integer, Long> domainLastItem;
    private Map<Integer, Map<Long, WeightedItemList>> domainItemPath;
    private Set<Long> forbiddenItems;
    private Map<Long, WeightedItemList> allItems;

    public PathRecommender() {
        domainLastItem = new ConcurrentHashMap<Integer, Long>();
        domainItemPath = new ConcurrentHashMap<Integer, Map<Long, WeightedItemList>>();
        forbiddenItems = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        allItems = new ConcurrentHashMap<Long, WeightedItemList>();
    }

    public static void main(String[] args) {
        PathRecommender pr = new PathRecommender();
        pr.init();
        String json = "{\"msg\":\"impression\",\"id\":69465,\"client\":{\"id\":3777},\"domain\":{\"id\":140},\"item\":{\"id\":90450,\"title\":\"Nothing but a test\",\"url\":\"http:\\/\\/www.example.com\\/articles\\/90450\",\"created\":1375713174,\"text\":\"Still nothing but a <strong>test<\\/strong>.\",\"img\":null,\"recommendable\":true},\"context\":{\"category\":{\"id\":99}},\"config\":{\"timeout\":1,\"recommend\":true,\"limit\":4},\"version\":\"1.0\"}";
        System.out.println(pr.recommend("" + JsonUtils.getClientId(json), JsonUtils.getItemIdFromImpression(json), "" + JsonUtils.getDomainId(json), json, "" + JsonUtils.getConfigLimitFromImpression(json)));
        json = "{\"msg\":\"impression\",\"id\":38380,\"client\":{\"id\":6346},\"domain\":{\"id\":875},\"item\":{\"id\":17383,\"title\":\"Nothing but a test\",\"url\":\"http:\\/\\/www.example.com\\/articles\\/17383\",\"created\":1375864006,\"text\":\"Still nothing but a <strong>test<\\/strong>.\",\"img\":null,\"recommendable\":true},\"context\":{\"category\":{\"id\":67}},\"config\":{\"timeout\":1,\"recommend\":true,\"limit\":4},\"version\":\"1.0\"},2013-08-07 11:26:46,625";
        System.out.println(pr.recommend("" + JsonUtils.getClientId(json), JsonUtils.getItemIdFromImpression(json), "" + JsonUtils.getDomainId(json), json, "" + JsonUtils.getConfigLimitFromImpression(json)));
    }

    public List<ContestItem> recommend(String _client, String _item, String _domain, String _description, String _limit) {
        final List<ContestItem> recList = new ArrayList<ContestItem>();

        int limit = Integer.parseInt(_limit);
        int domain = Integer.parseInt(_domain);
        long itemId = Long.parseLong(_item);

        final Set<Long> recItems = new HashSet<Long>();
        if (domainItemPath.containsKey(domain)) {
            final WeightedItemList path = domainItemPath.get(domain).get(itemId);
            recItems.add(itemId);
            if (path != null && !path.isEmpty()) {
                // sort the weighted list
                Collections.sort(path);
                // get the first N items (i.e., limit)
                int n = 0; // recList index
                int size = Math.min(limit, path.size());
                int i = 0; // path index
                while (n < size) {
                    WeightedItem wi = path.get(i);
                    long id = wi.getItemId();
                    i++;
                    if (forbiddenItems.contains(id) || itemId == id) {
                        continue; // ignore this item
                    }
                    recList.add(new ContestItem(id));
                    recItems.add(id);
                    n++;
                }
            }
        }
        completeList(recList, recItems, domainItemPath.get(domain), limit - recList.size(), forbiddenItems);
        completeList(recList, recItems, allItems, limit - recList.size(), forbiddenItems);

        return recList;
    }

    private static void completeList(List<ContestItem> recList, Set<Long> itemsAlreadyRecommended, Map<Long, WeightedItemList> domainItems, int howMany, Set<Long> forbiddenItems) {
        int n = 0;
        if (domainItems != null) {
            for (WeightedItemList wil : domainItems.values()) {
                for (WeightedItem wi : wil) {
                    if (n >= howMany) {
                        break;
                    }
                    long id = wi.getItemId();
                    if (!forbiddenItems.contains(id) && !itemsAlreadyRecommended.contains(id)) {
                        recList.add(new ContestItem(id));
                        itemsAlreadyRecommended.add(id);
                        n++;
                    }
                }
            }
        }
    }

    public void init() {
        FileFilter logFilter = new WildcardFileFilter("contest.log*");
        final File dir = new File(".");
        File[] logs = dir.listFiles(logFilter);
        for (File file : logs) {
            Scanner scnr = null;
            try {
                scnr = new Scanner(file, "US-ASCII");
            } catch (FileNotFoundException e) {
                logger.error(e.getMessage());
            }
            while (scnr.hasNextLine()) {
                String line = scnr.nextLine();
                line = line.substring(0, line.length() - 24);
                if (JsonUtils.isImpression(line)) {
                    impression(line);
                }
                if (JsonUtils.isFeedback(line)) {
                    feedback(line);
                }
            }
        }
        logger.debug("init finished");
    }

    public void impression(String _impression) {
        Integer domainId = JsonUtils.getDomainIdFromImpression(_impression);
        String item = JsonUtils.getItemIdFromImpression(_impression);
        Boolean recommendable = JsonUtils.getItemRecommendableFromImpression(_impression);

        if ((domainId != null) && (item != null)) {
            update(domainId, null, item, recommendable);
        }
    }

    private void update(int domainId, String source, String target, Boolean recommendable) {
        Long item = Long.parseLong(target);

        if (source != null) {
            update(domainId, null, source, recommendable);
            Long id = Long.parseLong(source);
            synchronized (this) {
                domainLastItem.put(domainId, id);
            }
        }

        Long lastItem = null;
        synchronized (this) {
            lastItem = domainLastItem.get(domainId);
            domainLastItem.put(domainId, item);
        }
        WeightedItemList toUpdate = null;
        if (lastItem == null) {
            toUpdate = new WeightedItemList();
            Map<Long, WeightedItemList> m = new ConcurrentHashMap<Long, WeightedItemList>();
            m.put(item, toUpdate);
            synchronized (this) {
                domainItemPath.put(domainId, m);
            }
        } else {
            synchronized (this) {
                toUpdate = domainItemPath.get(domainId).get(lastItem);
            }
            if (toUpdate == null) {
                toUpdate = new WeightedItemList();
                domainItemPath.get(domainId).put(lastItem, toUpdate);
            }
            toUpdate.add(new WeightedItem(target, item, System.currentTimeMillis()));
        }
        // all items
        WeightedItemList all = allItems.get(1L);
        if (all == null) {
            all = new WeightedItemList();
            allItems.put(1L, all);
        }
        all.add(new WeightedItem(target, item, System.currentTimeMillis()));
        if (recommendable != null && !recommendable.booleanValue()) {
            forbiddenItems.add(item);
        }
    }

    public void feedback(String _feedback) {
        Integer domainId = JsonUtils.getDomainIdFromFeedback(_feedback);
        String source = JsonUtils.getSourceIdFromFeedback(_feedback);
        String target = JsonUtils.getTargetIdFromFeedback(_feedback);

        if ((domainId != null) && (source != null) && (target != null)) {
            update(domainId, source, target, null);
        }
    }

    public void error(String _error) {
        logger.error(_error);
        String[] invalidItems = JsonUtils.getInvalidItemsFromError(_error);
        if (invalidItems != null) {
            // since domain is optional, we cannot store the forbidden items per domain
            for (String item : invalidItems) {
                forbiddenItems.add(Long.parseLong(item));
            }
        }
    }

    public void setProperties(Properties properties) {
    }

    public static class WeightedItem implements Serializable, Comparable<WeightedItem> {

        private String item;
        private long itemId;
        private long time;
        private int freq;

        public WeightedItem(String item, long itemId, long time) {
            this.item = item;
            this.itemId = itemId;
            this.time = time;
            this.freq = 1;
        }

        public String getItem() {
            return item;
        }

        public Long getItemId() {
            return itemId;
        }

        public int getFreq() {
            return freq;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public void setFreq(int freq) {
            this.freq = freq;
        }

        public long getTime() {
            return time;
        }

        public int compareTo(WeightedItem t) {
            int c = getFreq() - t.getFreq();
            if (c == 0) {
                long diff = getTime() - t.getTime();
                c = (diff == 0L ? getItemId().compareTo(t.getItemId()) : (diff < 0L ? -1 : 1));
            }
            return c;
        }

        @Override
        public String toString() {
            return "[" + item + "," + time + "," + freq + "]";
        }
    }

    public static class WeightedItemList extends ArrayList<WeightedItem> implements Serializable {

        private Map<Long, Integer> positions;
        private int curPos;

        public WeightedItemList() {
            super();

            positions = new ConcurrentHashMap<Long, Integer>();
            curPos = 0;
        }

        @Override
        public boolean add(WeightedItem e) {
            if (!positions.containsKey(e.getItemId())) {
                positions.put(e.getItemId(), curPos);
                curPos++;
                return super.add(e);
            } else {
                WeightedItem ee = get(positions.get(e.getItemId()));
                ee.setFreq(1 + ee.getFreq());
                ee.setTime(e.getTime());
                return false;
            }
        }
    }
}