package com.example.studentattendance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Student cards: ripple on touch, scale-up entrance, long-press ContextMenu. */
public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    public interface Listener {
        void onStudentClick(Student s);
        void onEdit(Student s);
        void onToggleAttendance(Student s);
        void onDelete(Student s);
        void onViewCode(Student s);
    }

    private final List<Student> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public StudentAdapter(Listener listener) { this.listener = listener; }

    /** @param replayAnimation true = play the entrance animation for all rows again */
    public void setData(List<Student> data, boolean replayAnimation) {
        items.clear();
        items.addAll(data);
        if (replayAnimation) lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StudentViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull StudentViewHolder h, int position) {
        Student s = items.get(position);
        Context c = h.itemView.getContext();
        h.avatar.setText(s.getName().isEmpty() ? "?" : s.getName().substring(0, 1).toUpperCase());
        h.name.setText(s.getName());
        h.meta.setText(c.getString(R.string.student_meta, s.getCode(), s.getClassName()));
        h.time.setText(s.isPresent() ? s.getTimestamp() : c.getString(R.string.not_checked_in));
        h.status.setText(s.isPresent() ? R.string.status_present : R.string.status_absent);
        h.status.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(c, s.isPresent() ? R.color.present : R.color.absent)));

        // EVENT: OnClickListener (ripple plays automatically on the MaterialCardView)
        h.itemView.setOnClickListener(v -> listener.onStudentClick(s));

        // ANIMATION TRIGGER: scale-up + fade-in the first time a row is bound
        if (position > lastAnimatedPosition) {
            android.view.animation.Animation a = AnimationUtils.loadAnimation(c, R.anim.item_enter);
            a.setStartOffset(Math.min(position, 8) * 45L);
            h.itemView.startAnimation(a);
            lastAnimatedPosition = position;
        }
    }

    @Override public void onViewDetachedFromWindow(@NonNull StudentViewHolder holder) {
        holder.itemView.clearAnimation();
    }

    @Override public int getItemCount() { return items.size(); }

    private boolean dispatchMenu(int id, Student s) {
        if (id == R.id.ctx_edit) listener.onEdit(s);
        else if (id == R.id.ctx_manual) listener.onToggleAttendance(s);
        else if (id == R.id.ctx_delete) listener.onDelete(s);
        else if (id == R.id.ctx_view_code) listener.onViewCode(s);
        else return false;
        return true;
    }

    class StudentViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name, meta, time, status;

        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tv_avatar);
            name = itemView.findViewById(R.id.tv_name);
            meta = itemView.findViewById(R.id.tv_meta);
            time = itemView.findViewById(R.id.tv_time);
            status = itemView.findViewById(R.id.tv_status);

            // EVENT: OnLongClickListener -> haptic tick; returning false lets the ContextMenu open
            itemView.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return false;
            });

            // CONTEXT MENU: built on long-press
            itemView.setOnCreateContextMenuListener((menu, v, info) -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Student s = items.get(pos);
                new MenuInflater(v.getContext()).inflate(R.menu.context_menu, menu);
                menu.setHeaderTitle(s.getName());
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setOnMenuItemClickListener(item -> dispatchMenu(item.getItemId(), s));
                }
            });
        }
    }
}
