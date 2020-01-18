package trac.svc.meta.dal.jdbc;

import com.google.protobuf.MessageLite;
import trac.common.metadata.*;
import trac.svc.meta.dal.IMetadataDal;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


public class JdbcMetadataDal extends JdbcBaseDal implements IMetadataDal {

    private static final int LATEST_TAG = -1;
    private static final int LATEST_VERSION = -1;

    private final JdbcTenantImpl tenants;
    private final JdbcReadImpl readSingle;
    private final JdbcReadBatchImpl readBatch;
    private final JdbcWriteBatchImpl writeBatch;


    public JdbcMetadataDal(JdbcDialect dialect, DataSource dataSource, Executor executor) {

        super(dialect, dataSource, executor);

        tenants = new JdbcTenantImpl();
        readSingle = new JdbcReadImpl();
        readBatch = new JdbcReadBatchImpl();
        writeBatch = new JdbcWriteBatchImpl();
    }

    public void startup() {

        try {
            executeDirect(tenants::loadTenantMap);
        }
        catch (SQLException e) {
            // TODO: StartupException
            throw new RuntimeException();
        }
    }

    public void shutdown() {

        // Noop
    }

    @Override
    public CompletableFuture<Void> saveNewObject(String tenant, Tag tag) {

        var parts = separateParts(tag);
        return saveNewObjects(tenant, parts);
    }

    @Override
    public CompletableFuture<Void> saveNewObjects(String tenant, List<Tag> tags) {

        var parts = separateParts(tags);
        return saveNewObjects(tenant, parts);
    }

    private CompletableFuture<Void> saveNewObjects(String tenant, ObjectParts parts) {

        return wrapTransaction(conn -> {

            var tenantId = tenants.getTenantId(tenant);

            long[] objectPk = writeBatch.writeObjectId(conn, tenantId, parts.objectType, parts.objectId);
            long[] defPk = writeBatch.writeObjectDefinition(conn, tenantId, objectPk, parts.version, parts.definition);
            long[] tagPk = writeBatch.writeTagRecord(conn, tenantId, defPk, parts.tagVersion);
            writeBatch.writeTagAttrs(conn, tenantId, tagPk, parts.tag);

            writeBatch.writeLatestVersion(conn, tenantId, objectPk, parts.version);
            writeBatch.writeLatestTag(conn, tenantId, defPk, parts.tagVersion);
        },
        (error, code) ->  JdbcError.handleDuplicateObjectId(error, code, parts));
    }

    @Override
    public CompletableFuture<Void> saveNewVersion(String tenant, Tag tag) {

        var parts = separateParts(tag);
        return saveNewVersions(tenant, parts);
    }

    @Override
    public CompletableFuture<Void> saveNewVersions(String tenant, List<Tag> tags) {

        var parts = separateParts(tags);
        return saveNewVersions(tenant, parts);
    }

    private CompletableFuture<Void> saveNewVersions(String tenant, ObjectParts parts) {

        return wrapTransaction(conn -> {

            prepareMappingTable(conn);

            var tenantId = tenants.getTenantId(tenant);
            var objectType = readBatch.readObjectTypeById(conn, tenantId, parts.objectId);

            checkObjectTypes(parts, objectType);

            long[] defPk = writeBatch.writeObjectDefinition(conn, tenantId, objectType.keys, parts.version, parts.definition);
            long[] tagPk = writeBatch.writeTagRecord(conn, tenantId, defPk, parts.tagVersion);
            writeBatch.writeTagAttrs(conn, tenantId, tagPk, parts.tag);

            writeBatch.updateLatestVersion(conn, tenantId, objectType.keys, parts.version);
            writeBatch.writeLatestTag(conn, tenantId, defPk, parts.tagVersion);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts),
        (error, code) ->  JdbcError.handleDuplicateObjectId(error, code, parts),
        (error, code) ->  JdbcError.newVersion_WrongType(error, code, parts));
    }

    @Override
    public CompletableFuture<Void> saveNewTag(String tenant, Tag tag) {

        var parts = separateParts(tag);
        return saveNewTags(tenant, parts);
    }

    @Override
    public CompletableFuture<Void> saveNewTags(String tenant, List<Tag> tags) {

        var parts = separateParts(tags);
        return saveNewTags(tenant, parts);
    }

    private CompletableFuture<Void> saveNewTags(String tenant, ObjectParts parts) {

        return wrapTransaction(conn -> {

            prepareMappingTable(conn);

            var tenantId = tenants.getTenantId(tenant);
            var objectType = readBatch.readObjectTypeById(conn, tenantId, parts.objectId);

            checkObjectTypes(parts, objectType);

            long[] defPk = readBatch.lookupDefinitionPk(conn, tenantId, objectType.keys, parts.version);
            long[] tagPk = writeBatch.writeTagRecord(conn, tenantId, defPk, parts.tagVersion);
            writeBatch.writeTagAttrs(conn, tenantId, tagPk, parts.tag);

            writeBatch.updateLatestTag(conn, tenantId, defPk, parts.tagVersion);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts),
        (error, code) ->  JdbcError.handleDuplicateObjectId(error, code, parts),
        (error, code) ->  JdbcError.newTag_WrongType(error, code, parts));
    }

    @Override
    public CompletableFuture<Void> preallocateObjectId(String tenant, ObjectType objectType, UUID objectId) {

        var parts = separateParts(objectType, objectId);
        return preallocateObjectIds(tenant, parts);
    }

    @Override
    public CompletableFuture<Void> preallocateObjectIds(String tenant, List<ObjectType> objectTypes, List<UUID> objectIds) {

        var parts = separateParts(objectTypes, objectIds);
        return preallocateObjectIds(tenant, parts);
    }

    private CompletableFuture<Void> preallocateObjectIds(String tenant, ObjectParts parts) {

        return wrapTransaction(conn -> {

            var tenantId = tenants.getTenantId(tenant);

            writeBatch.writeObjectId(conn, tenantId, parts.objectType, parts.objectId);
        },
        (error, code) ->  JdbcError.handleDuplicateObjectId(error, code, parts));
    }

    @Override
    public CompletableFuture<Void> savePreallocatedObject(String tenant, Tag tag) {

        var parts = separateParts(tag);
        return savePreallocatedObjects(tenant, parts);
    }

    @Override
    public CompletableFuture<Void> savePreallocatedObjects(String tenant, List<Tag> tags) {

        var parts = separateParts(tags);
        return savePreallocatedObjects(tenant, parts);
    }

    private CompletableFuture<Void> savePreallocatedObjects(String tenant, ObjectParts parts) {

        return wrapTransaction(conn -> {

            prepareMappingTable(conn);

            var tenantId = tenants.getTenantId(tenant);
            var objectType = readBatch.readObjectTypeById(conn, tenantId, parts.objectId);

            checkObjectTypes(parts, objectType);

            long[] defPk = writeBatch.writeObjectDefinition(conn, tenantId, objectType.keys, parts.version, parts.definition);
            long[] tagPk = writeBatch.writeTagRecord(conn, tenantId, defPk, parts.tagVersion);
            writeBatch.writeTagAttrs(conn, tenantId, tagPk, parts.tag);

            writeBatch.writeLatestVersion(conn, tenantId, objectType.keys, parts.version);
            writeBatch.writeLatestTag(conn, tenantId, defPk, parts.tagVersion);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts),   // TODO: different errors
        (error, code) ->  JdbcError.handleDuplicateObjectId(error, code, parts),  // TODO: different errors
        (error, code) ->  JdbcError.savePreallocated_WrongType(error, code, parts));
    }

    private void checkObjectTypes(ObjectParts parts, KeyedItems<ObjectType> existingTypes) throws JdbcException {

        for (int i = 0; i < parts.objectType.length; i++)
            if (parts.objectType[i] != existingTypes.items[i])
                throw new JdbcException(JdbcErrorCode.WRONG_OBJECT_TYPE.name(), JdbcErrorCode.WRONG_OBJECT_TYPE);
    }

    @Override public CompletableFuture<Tag>
    loadTag(String tenant, ObjectType objectType, UUID objectId, int objectVersion, int tagVersion) {

        var parts = assembleParts(objectType, objectId, objectVersion, tagVersion);

        return wrapTransaction(conn -> {

            var tenantId = tenants.getTenantId(tenant);

            var storedType = readSingle.readObjectTypeById(conn, tenantId, objectId);
            var definition = readSingle.readDefinitionByVersion(conn, tenantId, objectType, storedType.key, objectVersion);
            var tagStub = readSingle.readTagRecordByVersion(conn, tenantId, definition.key, tagVersion);
            var tagAttrs = readSingle.readTagAttrs(conn, tenantId, tagStub.key);

            // TODO: Unnecessary tag builder
            return buildTag(objectType, objectId, objectVersion, tagVersion, definition.item, tagAttrs);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts));
    }

    @Override public CompletableFuture<List<Tag>>
    loadTags(String tenant, List<ObjectType> objectTypes, List<UUID> objectIds, List<Integer> objectVersions, List<Integer> tagVersions) {

        var parts = assembleParts(objectTypes, objectIds, objectVersions, tagVersions);

        return wrapTransaction(conn -> {

            prepareMappingTable(conn);

            var tenantId = tenants.getTenantId(tenant);

            var storedType = readBatch.readObjectTypeById(conn, tenantId, parts.objectId);
            var definition = readBatch.readDefinitionByVersion(conn, tenantId, parts.objectType, storedType.keys, parts.version);
            var tag = readBatch.readTagRecordByVersion(conn, tenantId, definition.keys, parts.tagVersion);
            var tagAttrs = readBatch.readTagAttrs(conn, tenantId, tag.keys);

            // TODO: Unnecessary tag builder
            return buildTags(parts.objectType, parts.objectId, parts.version, parts.tagVersion, definition.items, tagAttrs);
        });
    }

    @Override public CompletableFuture<Tag>
    loadLatestTag(String tenant, ObjectType objectType, UUID objectId, int objectVersion) {

        var parts = assembleParts(objectType, objectId, objectVersion, LATEST_TAG);

        return wrapTransaction(conn -> {

            var tenantId = tenants.getTenantId(tenant);

            var storedType = readSingle.readObjectTypeById(conn, tenantId, objectId);
            var definition = readSingle.readDefinitionByVersion(conn, tenantId, objectType, storedType.key, objectVersion);
            var tagStub = readSingle.readTagRecordByLatest(conn, tenantId, definition.key);
            var tagAttrs = readSingle.readTagAttrs(conn, tenantId, tagStub.key);

            // TODO: Unnecessary tag builder
            return buildTag(objectType, objectId, objectVersion, tagStub.item.getTagVersion(), definition.item, tagAttrs);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts));
    }

    @Override public CompletableFuture<Tag>
    loadLatestVersion(String tenant, ObjectType objectType, UUID objectId) {

        var parts = assembleParts(objectType, objectId, LATEST_VERSION, LATEST_TAG);

        return wrapTransaction(conn -> {

            var tenantId = tenants.getTenantId(tenant);

            var storedType = readSingle.readObjectTypeById(conn, tenantId, objectId);
            var definition = readSingle.readDefinitionByLatest(conn, tenantId, objectType, storedType.key);
            var tagStub = readSingle.readTagRecordByLatest(conn, tenantId, definition.key);
            var tagAttrs = readSingle.readTagAttrs(conn, tenantId, tagStub.key);

            // TODO: Unnecessary tag builder
            return buildTag(objectType, objectId,
                    definition.item.version,
                    tagStub.item.getTagVersion(),
                    definition.item.item,
                    tagAttrs);
        },
        (error, code) -> JdbcError.handleMissingItem(error, code, parts));
    }


    // -----------------------------------------------------------------------------------------------------------------
    // OBJECT PARTS
    // -----------------------------------------------------------------------------------------------------------------

    static class ObjectParts {

        ObjectType[] objectType;
        UUID[] objectId;
        int[] version;
        int[] tagVersion;

        Tag[] tag;
        MessageLite[] definition;
    }


    private ObjectParts separateParts(Tag tag) {

        var parts = new ObjectParts();
        parts.objectType = new ObjectType[] {tag.getHeader().getObjectType()};
        parts.objectId = new UUID[] {MetadataCodec.decode(tag.getHeader().getId())};
        parts.version = new int[] {tag.getHeader().getVersion()};
        parts.tagVersion = new int[] {tag.getTagVersion()};

        parts.tag = new Tag[] {tag};
        parts.definition = new MessageLite[] {MetadataCodec.definitionForTag(tag)};

        return parts;
    }

    private ObjectParts separateParts(List<Tag> tags) {

        var objectHeaders = tags.stream().map(Tag::getHeader).toArray(ObjectHeader[]::new);

        var parts = new ObjectParts();
        parts.objectType = Arrays.stream(objectHeaders).map(ObjectHeader::getObjectType).toArray(ObjectType[]::new);
        parts.objectId = Arrays.stream(objectHeaders).map(ObjectHeader::getId).map(MetadataCodec::decode).toArray(UUID[]::new);
        parts.version = Arrays.stream(objectHeaders).mapToInt(ObjectHeader::getVersion).toArray();
        parts.tagVersion = tags.stream().mapToInt(Tag::getTagVersion).toArray();

        parts.tag = tags.toArray(Tag[]::new);
        parts.definition = tags.stream().map(MetadataCodec::definitionForTag).toArray(MessageLite[]::new);

        return parts;
    }

    private ObjectParts separateParts(ObjectType objectType, UUID objectId) {

        var parts = new ObjectParts();
        parts.objectType = new ObjectType[] {objectType};
        parts.objectId = new UUID[] {objectId};

        return parts;
    }

    private ObjectParts separateParts(List<ObjectType> objectTypes, List<UUID> objectIds) {

        var parts = new ObjectParts();
        parts.objectType = objectTypes.toArray(ObjectType[]::new);
        parts.objectId = objectIds.toArray(UUID[]::new);

        return parts;
    }

    private ObjectParts assembleParts(ObjectType type, UUID id, int version, int tagVersion) {

        var parts = new ObjectParts();
        parts.objectType = new ObjectType[] {type};
        parts.objectId = new UUID[] {id};
        parts.version = new int[] {version};
        parts.tagVersion = new int[] {tagVersion};

        return parts;
    }

    private ObjectParts assembleParts(List<ObjectType> types, List<UUID> ids, List<Integer> versions, List<Integer> tagVersions) {

        var parts = new ObjectParts();
        parts.objectType = types.toArray(ObjectType[]::new);
        parts.objectId = ids.toArray(UUID[]::new);
        parts.version = versions.stream().mapToInt(x -> x).toArray();
        parts.tagVersion = tagVersions.stream().mapToInt(x -> x).toArray();

        return parts;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // BUILD TAGS
    // -----------------------------------------------------------------------------------------------------------------

    private Tag buildTag(
            ObjectType objectType, UUID objectId,
            int objectVersion, int tagVersion,
            MessageLite definition,
            Map<String, PrimitiveValue> tagAttrs) {

        var header = ObjectHeader.newBuilder()
                .setObjectType(objectType)
                .setId(MetadataCodec.encode(objectId))
                .setVersion(objectVersion)
                .build();

        return MetadataCodec.tagForDefinition(objectType, definition)
                .setHeader(header)
                .setTagVersion(tagVersion)
                .putAllAttr(tagAttrs)
                .build();
    }

    private List<Tag> buildTags(
            ObjectType[] objectType, UUID[] objectId,
            int[] objectVersion, int[] tagVersion,
            MessageLite[] definition,
            Map<String, PrimitiveValue>[] tagAttrs) {

        var result = new ArrayList<Tag>(objectId.length);

        for (int i = 0; i < objectId.length; i++)
            result.add(i, buildTag(objectType[i], objectId[i], objectVersion[i], tagVersion[i], definition[i], tagAttrs[i]));

        return result;
    }
}
