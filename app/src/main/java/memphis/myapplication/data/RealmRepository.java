package memphis.myapplication.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.named_data.jndn.security.v2.CertificateV2;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import io.realm.Realm;
import io.realm.RealmResults;
import memphis.myapplication.data.RealmObjects.FilesInfo;
import memphis.myapplication.data.RealmObjects.FilesInfoRealm;
import memphis.myapplication.data.RealmObjects.PublishedContent;
import memphis.myapplication.data.RealmObjects.PublishedContentRealm;
import memphis.myapplication.data.RealmObjects.SelfCertificate;
import memphis.myapplication.data.RealmObjects.SelfCertificateRealm;
import memphis.myapplication.data.RealmObjects.User;
import memphis.myapplication.data.RealmObjects.UserRealm;
import timber.log.Timber;

public class RealmRepository {

    private static RealmRepository instance;
    public Realm realm;
    private static MutableLiveData<List<String>> friends;

    private RealmRepository() {
        realm = Realm.getDefaultInstance();
    }

    public static RealmRepository getInstance() {
        if (instance == null) {
            instance = new RealmRepository();

        }
        return instance;
    }

    public static RealmRepository getInstanceForNonUI() {
        return new RealmRepository();
    }

    public void createInstance() {
        if (realm.isClosed()) {
            realm = Realm.getDefaultInstance();
        }
    }

    public User setFriendship(String friendName) {
        realm.beginTransaction();
        UserRealm friend = realm.where(UserRealm.class).equalTo("username", friendName).findFirst();
        friend.setFriend(true);
        User user = userRealmToUser(friend);
        realm.commitTransaction();
        return user;
    }

    public int saveFriend(String friendName) {
        realm.beginTransaction();
        UserRealm friend = realm.where(UserRealm.class).equalTo("username", friendName).findFirst();
        if (friend == null) {
            realm.cancelTransaction();
            return -1;
        } else if (friend.isFriend()) {
            realm.cancelTransaction();
            return 1;
        } else if (friend.haveTrust()) {
            realm.cancelTransaction();
            return 3;
        }
        realm.cancelTransaction();
        return -1;
    }

    public User saveNewFriend(String friendName, String friendDomain, CertificateV2 certificateV2) {
        realm.beginTransaction();
        UserRealm friend = realm.where(UserRealm.class).equalTo("username", friendName).findFirst();
        if (friend == null)
            friend = realm.createObject(UserRealm.class, friendName);

        if (friendDomain != null)
            friend.setDomain(friendDomain);
        if (certificateV2 != null)
            friend.setCert(certificateV2);

        User user = userRealmToUser(friend);
        realm.commitTransaction();
        return user;
    }

    public User saveNewFriend(String friendName, boolean trust, CertificateV2 certificateV2) {
        realm.beginTransaction();
        UserRealm friend = realm.where(UserRealm.class).equalTo("username", friendName).findFirst();
        if (friend == null)
            friend = realm.createObject(UserRealm.class, friendName);

        if (certificateV2 != null)
            friend.setCert(certificateV2);
        friend.setFriend(true);
        friend.setTrust(trust);

        User user = userRealmToUser(friend);

        if (friends != null && friends.getValue() != null) {
            friends.getValue().add(user.getUsername());
            friends.postValue(friends.getValue());
        }

        realm.commitTransaction();
        return user;
    }

    public ArrayList<User> getAllFriends() {
        ArrayList<User> users = new ArrayList<>();
        realm.beginTransaction();
        RealmResults<UserRealm> userRealm = realm.where(UserRealm.class).equalTo("friend", true).findAll();
        for (UserRealm u : userRealm) {
            users.add(userRealmToUser(u));
        }
        realm.commitTransaction();
        return users;
    }

    public LiveData<List<String>> observeAllFriends() {
        if (friends == null) {
            friends = new MutableLiveData<>();
            List<String> friendsList = new ArrayList<>();
            for (User f : getAllFriends())
                friendsList.add(f.getUsername());
            friends.setValue(friendsList);
        }
        return friends;
    }

    public ArrayList<User> getPotentialFriends() {
        ArrayList<User> users = new ArrayList<>();
        realm.beginTransaction();
        RealmResults<UserRealm> potentialFriends = realm.where(UserRealm.class).equalTo("friend", false).findAll();
        for (UserRealm u : potentialFriends) {
            users.add(userRealmToUser(u));
        }
        realm.commitTransaction();
        return users;
    }

    public ArrayList<String> getFriendsofFriend(String friendName) {
        realm.beginTransaction();
        UserRealm newFriend = realm.where(UserRealm.class).equalTo("username", friendName).findFirst();
        User user = userRealmToUser(newFriend);
        realm.commitTransaction();
        return user.getFriends();
    }

    public ArrayList<User> getTrustedFriends() {
        realm.beginTransaction();
        RealmResults<UserRealm> trustedFriends = realm.where(UserRealm.class).equalTo("trust", true).findAll();
        ArrayList<User> users = new ArrayList<>();
        for (UserRealm u : trustedFriends) {
            users.add(userRealmToUser(u));
        }
        realm.commitTransaction();
        return users;
    }

    public User deleteFriendship(String friend) {
        realm.beginTransaction();
        UserRealm userRealm = realm.where(UserRealm.class).equalTo("username", friend).findFirst();
        userRealm.setFriend(false);
        User user = userRealmToUser(userRealm);
        if (friends != null && friends.getValue() != null && friends.getValue().contains(user.getUsername())) {
            friends.getValue().remove(user.getUsername());
            friends.setValue(friends.getValue());
        }
        realm.commitTransaction();
        return user;
    }

    public User getFriend(String friendName) {
        realm.beginTransaction();
        User user = userRealmToUser(realm.where(UserRealm.class).equalTo("username", friendName).findFirst());
        realm.commitTransaction();
        return user;
    }

    public SelfCertificate getFriendCert(String friendName) {
        realm.beginTransaction();
        SelfCertificate selfCertificate = selfCertificateRealmToSelfCertificate(realm.where(SelfCertificateRealm.class).equalTo("username", friendName).findFirst());
        realm.commitTransaction();
        return selfCertificate;
    }

    public void setFriendCert(String friendName, CertificateV2 certificateV2) {
        realm.beginTransaction();
        SelfCertificateRealm realmCertificate = realm.where(SelfCertificateRealm.class).equalTo("username", friendName).findFirst();
        if (realmCertificate == null) {
            realmCertificate = realm.createObject(SelfCertificateRealm.class, friendName);
        }
        realmCertificate.setCert(certificateV2);

        realm.commitTransaction();
    }

    public PublishedContent getPublishedContent(String filename) {
        realm.beginTransaction();
        PublishedContent publishedContent = publishedContentRealmTopublishedContent(realm.where(PublishedContentRealm.class).equalTo("filename", filename).findFirst());
        realm.commitTransaction();
        return publishedContent;
    }

    public void addKey(String path, SecretKey secretKey) {
        realm.beginTransaction();
        PublishedContentRealm contentKey = realm.createObject(PublishedContentRealm.class, path);
        contentKey.addKey(secretKey);
        realm.commitTransaction();
    }

    public void setFriendCertificate(String friendName, CertificateV2 certificateV2) {
        realm.beginTransaction();
        SelfCertificateRealm certificate = realm.where(SelfCertificateRealm.class).equalTo("username", friendName).findFirst();
        if (certificate == null) {
            certificate = realm.createObject(SelfCertificateRealm.class, friendName);
        }
        certificate.setCert(certificateV2);
        realm.commitTransaction();
    }

    public User addFriendToUser(String username, String friend) {
        realm.beginTransaction();
        UserRealm sharingUser = realm.where(UserRealm.class).equalTo("username", username).findFirst();
        sharingUser.addFriend(friend);

        User user = userRealmToUser(sharingUser);
        realm.commitTransaction();

        return user;
    }

    public void saveNewFile(String filename, boolean isFeed, boolean location, String producer) {
        realm.beginTransaction();
        FilesInfoRealm filesInfoRealm = realm.where(FilesInfoRealm.class).equalTo("filename", filename).findFirst();
        if (filesInfoRealm == null) {
            filesInfoRealm = realm.createObject(FilesInfoRealm.class, filename);
            filesInfoRealm.setProducer(producer);

            filesInfoRealm.setFeed(isFeed);
            filesInfoRealm.setLocation(location);
        }

        realm.commitTransaction();
    }


    public void saveNewFile(FilesInfo filesInfo) {
        realm.beginTransaction();
        FilesInfoRealm filesInfoRealm = realm.where(FilesInfoRealm.class).equalTo("filename", filesInfo.filename).findFirst();
        if (filesInfoRealm == null) {
            filesInfoRealm = realm.createObject(FilesInfoRealm.class, filesInfo.filename);
        }

        filesInfoRealm.setProducer(filesInfo.producer);
        filesInfoRealm.setFilePath(filesInfo.filePath);

        filesInfoRealm.setFeed(filesInfo.feed);
        filesInfoRealm.setLocation(filesInfo.location);

        realm.commitTransaction();
    }

    public FilesInfo getFileInfo(String filename) {
        realm.beginTransaction();
        FilesInfoRealm filesInfoRealm = realm.where(FilesInfoRealm.class).equalTo("filename", filename).findFirst();
        FilesInfo filesInfo = null;
        if (filesInfoRealm != null) {
            filesInfo = fileInfoRealmToFileInfo(filesInfoRealm);
        }

        realm.commitTransaction();
        return filesInfo;
    }

    public void deleteFileInfo(String filename) {
        realm.beginTransaction();
        RealmResults<FilesInfoRealm> filesInfoRealms = realm.where(FilesInfoRealm.class).equalTo("filename", filename).findAll();
        if (filesInfoRealms != null) {
            filesInfoRealms.deleteAllFromRealm();
        }

        realm.commitTransaction();
    }

    public static User userRealmToUser(UserRealm userRealm) {
        User user = new User();
        if (userRealm == null) return user;
        user.setUsername(userRealm.getUsername());
        user.setTrust(userRealm.haveTrust());
        user.setDomain(userRealm.getDomain());
        user.setCert(userRealm.getCertByreArray());
        user.setFriend(userRealm.isFriend());
        user.setFriends(userRealm.getFriends());
        return user;
    }

    public static SelfCertificate selfCertificateRealmToSelfCertificate(SelfCertificateRealm selfCertificateRealm) {
        SelfCertificate selfCertificate = new SelfCertificate();
        if (selfCertificateRealm == null) return selfCertificate;

        selfCertificate.setCert(selfCertificateRealm.getCertInByte());
        return selfCertificate;
    }

    public static PublishedContent publishedContentRealmTopublishedContent(PublishedContentRealm publishedContentRealm) {
        PublishedContent publishedContent = new PublishedContent();
        if (publishedContentRealm == null) return publishedContent;

        publishedContent.setKey(publishedContentRealm.getKey());
        publishedContent.setFilename(publishedContentRealm.getFilename());

        return publishedContent;
    }

    public static FilesInfo fileInfoRealmToFileInfo(FilesInfoRealm filesInfoRealm) {
        FilesInfo filesInfo = new FilesInfo();
        filesInfo.filename = filesInfoRealm.getFilename();
        filesInfo.filePath = filesInfoRealm.getFilePath();
        filesInfo.producer = filesInfoRealm.getProducer();
        filesInfo.feed = filesInfoRealm.isFeed();
        filesInfo.location = filesInfoRealm.isLocation();

        return filesInfo;
    }

    public void close() {
        realm.close();
    }
}