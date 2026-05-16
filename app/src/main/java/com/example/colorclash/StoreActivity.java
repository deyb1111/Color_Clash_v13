package com.example.colorclash;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.colorclash.database.DatabaseManager;
import java.util.ArrayList;
import java.util.List;

/**
 * StoreActivity — Problem 4 fix.
 *
 * Profile system (Option B):
 *   - Spinner shows all saved player profiles instead of a free-text field.
 *   - "+ Add" button lets the player create a new profile on the spot.
 *   - Selecting a profile from the spinner instantly loads their gold + items.
 *   - Falls back gracefully if no profiles have been saved yet.
 */
public class StoreActivity extends AppCompatActivity {

    private DatabaseManager db;
    private ProfileManager  profileManager;
    private String          playerName = "";

    private RecyclerView recycler;
    private TextView     goldBalanceText;
    private Spinner      profileSpinner;
    private TextView     emptyHint;

    private List<String>        profileNames = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        db             = DatabaseManager.getInstance(this);
        profileManager = new ProfileManager(this);

        goldBalanceText = findViewById(R.id.store_gold_balance);
        profileSpinner  = findViewById(R.id.store_profile_spinner);
        emptyHint       = findViewById(R.id.store_empty_hint);
        recycler        = findViewById(R.id.store_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        // "+ Add" button
        Button addBtn = findViewById(R.id.store_add_profile_btn);
        addBtn.setOnClickListener(v -> showAddProfileDialog());

        findViewById(R.id.store_back).setOnClickListener(v -> finish());

        setupSpinner();
    }

    // ── Spinner ───────────────────────────────────────────────────────────────

    private void setupSpinner() {
        profileNames = new ArrayList<>(profileManager.getProfileNames());

        spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, profileNames);
        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(spinnerAdapter);

        if (profileNames.isEmpty()) {
            if (emptyHint != null) emptyHint.setVisibility(View.VISIBLE);
            profileSpinner.setVisibility(View.GONE);
        } else {
            if (emptyHint != null) emptyHint.setVisibility(View.GONE);
            profileSpinner.setVisibility(View.VISIBLE);

            // Auto-load the primary player's profile
            String primary = profileManager.getPrimaryName();
            int idx = profileNames.indexOf(primary);
            if (idx >= 0) profileSpinner.setSelection(idx);

            profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    playerName = profileNames.get(pos);
                    db.ensurePlayer(playerName);
                    refreshStore();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Load immediately for the initially selected profile
            playerName = profileNames.get(profileSpinner.getSelectedItemPosition());
            db.ensurePlayer(playerName);
            refreshStore();
        }
    }

    private void refreshSpinner() {
        profileNames.clear();
        profileNames.addAll(profileManager.getProfileNames());
        spinnerAdapter.notifyDataSetChanged();

        if (profileNames.isEmpty()) {
            if (emptyHint != null) emptyHint.setVisibility(View.VISIBLE);
            profileSpinner.setVisibility(View.GONE);
        } else {
            if (emptyHint != null) emptyHint.setVisibility(View.GONE);
            profileSpinner.setVisibility(View.VISIBLE);
            int idx = profileNames.indexOf(playerName);
            if (idx >= 0) profileSpinner.setSelection(idx);
        }
    }

    // ── Add profile dialog ────────────────────────────────────────────────────

    private void showAddProfileDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter player name");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Add Profile")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    profileManager.addProfile(name);
                    db.ensurePlayer(name);
                    playerName = name;
                    refreshSpinner();
                    refreshStore();
                    Toast.makeText(this, "Profile \"" + name + "\" added!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Store content ─────────────────────────────────────────────────────────

    private void refreshStore() {
        if (playerName.isEmpty()) return;
        int gold = db.getGold(playerName);
        goldBalanceText.setText("Gold: " + gold + " 🪙");
        List<DatabaseManager.StoreItem> items = db.getStoreItems(playerName);
        recycler.setAdapter(new StoreAdapter(items));
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    private class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.VH> {

        private final List<DatabaseManager.StoreItem> items;
        StoreAdapter(List<DatabaseManager.StoreItem> items) { this.items = items; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_store_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DatabaseManager.StoreItem item = items.get(pos);
            h.nameText.setText(item.name);
            h.typeText.setText(item.type.toUpperCase());
            h.typeText.setBackgroundColor(
                    "cosmetic".equals(item.type) ? 0xFF5C6BC0 : 0xFF00897B);
            h.priceText.setText(item.priceGold + " 🪙");

            if (item.owned) {
                h.buyBtn.setVisibility(View.GONE);
                h.equipBtn.setVisibility(View.VISIBLE);
                h.equipBtn.setText(item.equipped ? "Unequip" : "Equip");
                h.equipBtn.setOnClickListener(v -> {
                    if (item.equipped) db.unequipItem(playerName, item.id, item.type);
                    else               db.equipItem(playerName,   item.id, item.type);
                    refreshStore();
                });
            } else {
                h.equipBtn.setVisibility(View.GONE);
                h.buyBtn.setVisibility(View.VISIBLE);
                h.buyBtn.setEnabled(db.getGold(playerName) >= item.priceGold);
                h.buyBtn.setOnClickListener(v -> {
                    boolean ok = db.purchaseItem(playerName, item.id, item.priceGold);
                    Toast.makeText(StoreActivity.this,
                            ok ? "Purchased " + item.name + "!" : "Not enough gold!",
                            Toast.LENGTH_SHORT).show();
                    refreshStore();
                });
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView nameText, typeText, priceText;
            Button   buyBtn, equipBtn;
            VH(View v) {
                super(v);
                nameText  = v.findViewById(R.id.store_item_name);
                typeText  = v.findViewById(R.id.store_item_type);
                priceText = v.findViewById(R.id.store_item_price);
                buyBtn    = v.findViewById(R.id.store_buy_btn);
                equipBtn  = v.findViewById(R.id.store_equip_btn);
            }
        }
    }
}
