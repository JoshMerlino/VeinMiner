package wtf.choco.veinminer.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import wtf.choco.veinminer.platform.ItemType;

/**
 * A registry to which {@link VeinMinerToolCategory VeinMinerToolCategories} may be registered.
 */
public final class ToolCategoryRegistry {

    private final Map<String, VeinMinerToolCategory> categories = new HashMap<>();

    /**
     * Register the given {@link VeinMinerToolCategory}.
     *
     * @param category the category to register
     */
    public void register(@NotNull VeinMinerToolCategory category) {
        this.categories.put(category.getId().toLowerCase(), category);
    }

    /**
     * Get the {@link VeinMinerToolCategory} with the given id.
     *
     * @param id the id of the category to get
     *
     * @return the category, or null if none exists
     */
    @Nullable
    public VeinMinerToolCategory get(@NotNull String id) {
        return categories.get(id.toLowerCase());
    }

    /**
     * Get the {@link VeinMinerToolCategory} that contains the given {@link ItemType}. There is
     * no guarantee as to which category will be returned if more than one category contains the
     * provided ItemType.
     *
     * @param itemType the item type
     *
     * @return the corresponding tool category, or null if no category contains the item
     */
    @Nullable
    public VeinMinerToolCategory get(@NotNull ItemType itemType) {
        for (VeinMinerToolCategory category : categories.values()) {
            if (category.containsItem(itemType)) {
                return category;
            }
        }

        return null;
    }

    /**
     * Unregister the given {@link VeinMinerToolCategory}.
     *
     * @param category the category to unregister
     *
     * @return true if unregistered, false if the category was not registered
     */
    public boolean unregister(@NotNull VeinMinerToolCategory category) {
        return (unregister(category.getId()) != null);
    }

    /**
     * Unregister the {@link VeinMinerToolCategory} with the given id.
     *
     * @param id the id of the category
     *
     * @return the category that was unregistered, or null if not registered
     */
    @Nullable
    public VeinMinerToolCategory unregister(@NotNull String id) {
        return categories.remove(id.toLowerCase());
    }

    /**
     * Get the amount of registered tool categories.
     *
     * @return size of this registry
     */
    public int size() {
        return categories.size();
    }

    /**
     * Get all registered {@link VeinMinerToolCategory VeinMinerToolCategories}.
     *
     * @return all categories
     */
    @NotNull
    @UnmodifiableView
    public Collection<? extends VeinMinerToolCategory> getAll() {
        return Collections.unmodifiableCollection(categories.values());
    }

    /**
     * Unregister all tool categories.
     */
    public void unregisterAll() {
        this.categories.clear();
    }

}